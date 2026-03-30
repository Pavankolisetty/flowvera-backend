package com.workly.service;

import com.workly.dto.AdminAttendanceEmployeeDto;
import com.workly.dto.AttendanceCalendarDayDto;
import com.workly.dto.AttendanceDaySummaryDto;
import com.workly.dto.AttendanceSessionDto;
import com.workly.dto.EmployeeAttendanceOverviewDto;
import com.workly.entity.AttendanceSession;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.repo.AttendanceSessionRepository;
import com.workly.repo.EmployeeRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayCalendarService holidayCalendarService;

    @Value("${attendance.absent-upper-limit-minutes:90}")
    private long absentUpperLimitMinutes;

    @Value("${attendance.partial-upper-limit-minutes:270}")
    private long partialUpperLimitMinutes;

    @Value("${attendance.inactive-grace-minutes:3}")
    private long inactiveGraceMinutes;

    @Value("${app.timezone:Asia/Kolkata}")
    private String appTimezone;

    @Override
    @Transactional
    public EmployeeAttendanceOverviewDto getEmployeeOverview(String empId, String sessionKey, YearMonth month) {
        closeExpiredSessions();
        YearMonth targetMonth = month == null ? currentYearMonth() : month;
        return buildOverview(empId, sessionKey, 7, targetMonth);
    }

    @Override
    @Transactional
    public EmployeeAttendanceOverviewDto clockIn(String empId, String sessionKey) {
        closeExpiredSessions();
        Employee employee = getEmployee(empId);
        LocalDateTime now = currentDateTime();

        Optional<AttendanceSession> existingSession =
            attendanceSessionRepository.findByEmployeeEmpIdAndSessionKeyAndActiveTrue(empId, sessionKey);

        AttendanceSession session = existingSession.orElseGet(AttendanceSession::new);
        session.setEmployee(employee);
        session.setSessionKey(sessionKey);
        session.setClockInAt(existingSession.map(AttendanceSession::getClockInAt).orElse(now));
        session.setSessionDate(session.getClockInAt().toLocalDate());
        session.setLastActivityAt(now);
        session.setActive(true);
        session.setClockOutAt(null);
        session.setCloseReason(null);
        attendanceSessionRepository.save(session);

        return buildOverview(empId, sessionKey, 7, currentYearMonth());
    }

    @Override
    @Transactional
    public EmployeeAttendanceOverviewDto heartbeat(String empId, String sessionKey) {
        closeExpiredSessions();
        AttendanceSession session = attendanceSessionRepository
            .findByEmployeeEmpIdAndSessionKeyAndActiveTrue(empId, sessionKey)
            .orElseThrow(() -> new IllegalArgumentException("No active attendance session found."));
        session.setLastActivityAt(currentDateTime());
        attendanceSessionRepository.save(session);
        return buildOverview(empId, sessionKey, 7, currentYearMonth());
    }

    @Override
    @Transactional
    public EmployeeAttendanceOverviewDto clockOut(String empId, String sessionKey) {
        closeExpiredSessions();
        AttendanceSession session = attendanceSessionRepository
            .findByEmployeeEmpIdAndSessionKeyAndActiveTrue(empId, sessionKey)
            .orElseThrow(() -> new IllegalArgumentException("No active attendance session found."));
        closeSession(session, currentDateTime(), "CLOCK_OUT");
        return buildOverview(empId, sessionKey, 7, currentYearMonth());
    }

    @Override
    @Transactional
    public void handleLogout(String empId, String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        closeExpiredSessions();
        attendanceSessionRepository.findByEmployeeEmpIdAndSessionKeyAndActiveTrue(empId, sessionKey)
            .ifPresent(session -> closeSession(session, currentDateTime(), "LOGOUT"));
    }

    @Override
    @Transactional
    public List<AdminAttendanceEmployeeDto> getTodayAttendanceForAdmin() {
        closeExpiredSessions();
        LocalDate today = currentDate();
        LocalDateTime rangeStart = today.minusDays(7).atStartOfDay();
        List<AttendanceSession> sessions =
            attendanceSessionRepository.findByClockInAtGreaterThanEqualOrderByClockInAtDesc(rangeStart);

        Map<String, List<AttendanceSession>> sessionsByEmployee = new HashMap<>();
        for (AttendanceSession session : sessions) {
            sessionsByEmployee.computeIfAbsent(session.getEmployee().getEmpId(), key -> new ArrayList<>()).add(session);
        }

        List<AdminAttendanceEmployeeDto> response = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
            if (employee.getRole() == Role.ADMIN) {
                continue;
            }

            AdminAttendanceEmployeeDto dto = new AdminAttendanceEmployeeDto();
            dto.setEmpId(employee.getEmpId());
            dto.setName(employee.getName());
            dto.setDesignation(employee.getDesignation());
            dto.setToday(buildDaySummary(
                sessionsByEmployee.getOrDefault(employee.getEmpId(), List.of()),
                today,
                currentDateTime()
            ));
            response.add(dto);
        }

        response.sort((left, right) -> {
            boolean leftWorking = Optional.ofNullable(left.getToday())
                .map(AttendanceDaySummaryDto::isCurrentlyWorking)
                .orElse(false);
            boolean rightWorking = Optional.ofNullable(right.getToday())
                .map(AttendanceDaySummaryDto::isCurrentlyWorking)
                .orElse(false);

            if (leftWorking != rightWorking) {
                return Boolean.compare(rightWorking, leftWorking);
            }

            return String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
        });
        return response;
    }

    @Override
    @Transactional
    public EmployeeAttendanceOverviewDto getEmployeeHistory(String empId, int days, String sessionKey, YearMonth month) {
        closeExpiredSessions();
        return buildOverview(
            empId,
            sessionKey,
            Math.max(1, Math.min(days, 30)),
            month == null ? currentYearMonth() : month
        );
    }

    @Transactional
    protected void closeExpiredSessions() {
        LocalDateTime cutoff = currentDateTime().minusMinutes(inactiveGraceMinutes);
        List<AttendanceSession> staleSessions =
            attendanceSessionRepository.findByActiveTrueAndLastActivityAtBefore(cutoff);
        for (AttendanceSession session : staleSessions) {
            closeSession(session, session.getLastActivityAt().plusMinutes(inactiveGraceMinutes), "TIMEOUT");
        }
    }

    private EmployeeAttendanceOverviewDto buildOverview(String empId, String sessionKey, int days, YearMonth month) {
        Employee employee = getEmployee(empId);
        LocalDate joinedDate = resolveJoinedDate(employee);
        LocalDate startDate = currentDate().minusDays(days - 1L);
        LocalDateTime rangeStart = startDate.minusDays(1).atStartOfDay();
        List<AttendanceSession> sessions =
            attendanceSessionRepository.findByEmployeeEmpIdAndClockInAtGreaterThanEqualOrderByClockInAtDesc(empId, rangeStart);

        Map<LocalDate, AttendanceDaySummaryDto> dayMap = new LinkedHashMap<>();
        LocalDate today = currentDate();
        LocalDateTime now = currentDateTime();

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = today.minusDays(offset);
            dayMap.put(date, buildDaySummary(sessions, date, now));
        }

        AttendanceSessionDto currentSession = attendanceSessionRepository
            .findByEmployeeEmpIdAndSessionKeyAndActiveTrue(empId, sessionKey == null ? "" : sessionKey)
            .map(session -> toSessionDto(session, now))
            .orElse(null);

        List<AttendanceSession> activeSessions =
            attendanceSessionRepository.findByEmployeeEmpIdAndActiveTrue(empId);

        EmployeeAttendanceOverviewDto dto = new EmployeeAttendanceOverviewDto();
        dto.setEmpId(empId);
        dto.setEmployeeName(employee.getName());
        dto.setToday(dayMap.get(today));
        dto.setCurrentSession(currentSession);
        dto.setActiveSessionCount(activeSessions.size());
        dto.setWeeklyWorkedMinutes(dayMap.values().stream().mapToLong(AttendanceDaySummaryDto::getWorkedMinutes).sum());
        dto.setRecentDays(new ArrayList<>(dayMap.values()));
        dto.setJoinedDate(joinedDate);
        dto.setCalendarMonth(month.atDay(1));
        dto.setCalendarDays(buildCalendarDays(employee, month, joinedDate));
        return dto;
    }

    private List<AttendanceCalendarDayDto> buildCalendarDays(Employee employee, YearMonth month, LocalDate joinedDate) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDateTime fetchStart = monthStart.minusDays(1).atStartOfDay();
        List<AttendanceSession> sessions = attendanceSessionRepository
            .findByEmployeeEmpIdAndClockInAtGreaterThanEqualOrderByClockInAtDesc(employee.getEmpId(), fetchStart);
        Map<LocalDate, String> holidays = holidayCalendarService.getNationalHolidays(month);
        List<AttendanceCalendarDayDto> calendarDays = new ArrayList<>();
        LocalDate today = currentDate();
        LocalDateTime now = currentDateTime();

        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            AttendanceDaySummaryDto summary = buildDaySummary(sessions, date, now);
            AttendanceCalendarDayDto dayDto = new AttendanceCalendarDayDto();
            dayDto.setDate(date);
            dayDto.setWorkedMinutes(summary.getWorkedMinutes());
            dayDto.setFirstClockInAt(summary.getFirstClockInAt());
            dayDto.setLastClockOutAt(summary.getLastClockOutAt());
            dayDto.setLastActivityAt(summary.getLastActivityAt());
            dayDto.setBeforeJoiningDate(date.isBefore(joinedDate));
            dayDto.setFutureDate(date.isAfter(today));
            dayDto.setHoliday(holidays.containsKey(date));
            dayDto.setHolidayName(holidays.get(date));
            boolean weeklyOff = date.getDayOfWeek() == DayOfWeek.SUNDAY;

            if (dayDto.isBeforeJoiningDate()) {
                dayDto.setStatus("NOT_JOINED");
            } else if ((dayDto.isHoliday() || weeklyOff) && summary.getWorkedMinutes() == 0) {
                dayDto.setStatus("HOLIDAY");
                if (dayDto.getHolidayName() == null && weeklyOff) {
                    dayDto.setHolidayName("Weekly off");
                }
            } else if (dayDto.isFutureDate()) {
                dayDto.setStatus("UPCOMING");
            } else {
                dayDto.setStatus(summary.getStatus());
            }

            calendarDays.add(dayDto);
        }

        return calendarDays;
    }

    private AttendanceDaySummaryDto buildDaySummary(List<AttendanceSession> sessions, LocalDate date, LocalDateTime now) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<TimeRange> ranges = new ArrayList<>();
        LocalDateTime firstClockIn = null;
        LocalDateTime lastClockOut = null;
        LocalDateTime latestActivity = null;
        int activeSessions = 0;

        for (AttendanceSession session : sessions) {
            LocalDateTime effectiveStart = session.getClockInAt();
            LocalDateTime effectiveEnd = resolveSessionEnd(session, now);

            if (effectiveEnd == null || !effectiveEnd.isAfter(dayStart) || !effectiveStart.isBefore(dayEnd)) {
                continue;
            }

            LocalDateTime clippedStart = effectiveStart.isAfter(dayStart) ? effectiveStart : dayStart;
            LocalDateTime clippedEnd = effectiveEnd.isBefore(dayEnd) ? effectiveEnd : dayEnd;
            if (!clippedEnd.isAfter(clippedStart)) {
                continue;
            }

            ranges.add(new TimeRange(clippedStart, clippedEnd));
            if (firstClockIn == null || effectiveStart.isBefore(firstClockIn)) {
                firstClockIn = effectiveStart;
            }
            if (lastClockOut == null || effectiveEnd.isAfter(lastClockOut)) {
                lastClockOut = effectiveEnd;
            }
            if (latestActivity == null || session.getLastActivityAt().isAfter(latestActivity)) {
                latestActivity = session.getLastActivityAt();
            }
            if (session.isActive()) {
                activeSessions++;
            }
        }

        long workedMinutes = mergeAndCountMinutes(ranges);
        AttendanceDaySummaryDto dto = new AttendanceDaySummaryDto();
        dto.setDate(date);
        dto.setWorkedMinutes(workedMinutes);
        dto.setCurrentlyWorking(date.equals(currentDate()) && activeSessions > 0);
        dto.setActiveSessions(activeSessions);
        dto.setFirstClockInAt(firstClockIn);
        dto.setLastClockOutAt(lastClockOut);
        dto.setLastActivityAt(latestActivity);
        dto.setStatus(resolveStatus(dto));
        return dto;
    }

    private String resolveStatus(AttendanceDaySummaryDto summary) {
        if (summary.isCurrentlyWorking()) {
            return "CLOCKED_IN";
        }
        if (summary.getWorkedMinutes() <= absentUpperLimitMinutes) {
            return "ABSENT";
        }
        if (summary.getWorkedMinutes() <= partialUpperLimitMinutes) {
            return "PARTIAL";
        }
        if (summary.getWorkedMinutes() > partialUpperLimitMinutes) {
            return "PRESENT";
        }
        return "ABSENT";
    }

    private long mergeAndCountMinutes(List<TimeRange> ranges) {
        if (ranges.isEmpty()) {
            return 0;
        }
        ranges.sort(Comparator.comparing(TimeRange::start));
        LocalDateTime currentStart = ranges.get(0).start();
        LocalDateTime currentEnd = ranges.get(0).end();
        long totalMinutes = 0;

        for (int index = 1; index < ranges.size(); index++) {
            TimeRange next = ranges.get(index);
            if (!next.start().isAfter(currentEnd)) {
                if (next.end().isAfter(currentEnd)) {
                    currentEnd = next.end();
                }
                continue;
            }

            totalMinutes += Duration.between(currentStart, currentEnd).toMinutes();
            currentStart = next.start();
            currentEnd = next.end();
        }

        totalMinutes += Duration.between(currentStart, currentEnd).toMinutes();
        return Math.max(totalMinutes, 0);
    }

    private LocalDateTime resolveSessionEnd(AttendanceSession session, LocalDateTime now) {
        if (session.getClockOutAt() != null) {
            return session.getClockOutAt();
        }
        if (session.isActive()) {
            return now;
        }
        return session.getLastActivityAt().plusMinutes(inactiveGraceMinutes);
    }

    private AttendanceSessionDto toSessionDto(AttendanceSession session, LocalDateTime now) {
        AttendanceSessionDto dto = new AttendanceSessionDto();
        dto.setId(session.getId());
        dto.setSessionKey(session.getSessionKey());
        dto.setClockInAt(session.getClockInAt());
        dto.setLastActivityAt(session.getLastActivityAt());
        dto.setClockOutAt(session.getClockOutAt());
        dto.setActive(session.isActive());
        dto.setCloseReason(session.getCloseReason());
        dto.setTrackedMinutes(Math.max(0, Duration.between(session.getClockInAt(), resolveSessionEnd(session, now)).toMinutes()));
        return dto;
    }

    private void closeSession(AttendanceSession session, LocalDateTime endedAt, String reason) {
        LocalDateTime safeEnd = endedAt.isBefore(session.getClockInAt()) ? session.getClockInAt() : endedAt;
        session.setClockOutAt(safeEnd);
        session.setLastActivityAt(safeEnd.isAfter(session.getLastActivityAt()) ? safeEnd : session.getLastActivityAt());
        session.setActive(false);
        session.setCloseReason(reason);
        attendanceSessionRepository.save(session);
    }

    private Employee getEmployee(String empId) {
        return employeeRepository.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
    }

    private LocalDate resolveJoinedDate(Employee employee) {
        if (employee.getCreatedAt() == null) {
            employee.setCreatedAt(currentDateTime());
            employeeRepository.save(employee);
        }
        return employee.getCreatedAt().toLocalDate();
    }

    private ZoneId appZone() {
        return ZoneId.of(appTimezone);
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.now(appZone());
    }

    private LocalDate currentDate() {
        return LocalDate.now(appZone());
    }

    private YearMonth currentYearMonth() {
        return YearMonth.now(appZone());
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }
}
