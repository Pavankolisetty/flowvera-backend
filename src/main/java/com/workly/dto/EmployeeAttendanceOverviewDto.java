package com.workly.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class EmployeeAttendanceOverviewDto {
    private String empId;
    private String employeeName;
    private AttendanceDaySummaryDto today;
    private AttendanceSessionDto currentSession;
    private int activeSessionCount;
    private long weeklyWorkedMinutes;
    private List<AttendanceDaySummaryDto> recentDays;
    private LocalDate joinedDate;
    private LocalDate calendarMonth;
    private List<AttendanceCalendarDayDto> calendarDays;
}
