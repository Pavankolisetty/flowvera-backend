package com.workly.service;

import com.workly.dto.LeaveBalanceResponse;
import com.workly.dto.LeaveEmployeeOptionDto;
import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestResponse;
import com.workly.entity.Employee;
import com.workly.entity.LeaveApprovalToken;
import com.workly.entity.LeaveDayPart;
import com.workly.entity.LeaveRequest;
import com.workly.entity.LeaveRequestDependency;
import com.workly.entity.LeaveRequestStatus;
import com.workly.entity.LeaveRequestType;
import com.workly.entity.Role;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.LeaveApprovalTokenRepository;
import com.workly.repo.LeaveRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private static final Collection<LeaveRequestStatus> ACTIVE_STATUSES =
        List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LeaveRequestRepository leaveRequestRepo;
    private final LeaveApprovalTokenRepository tokenRepo;
    private final EmployeeRepository employeeRepo;
    private final MailDeliveryService mailDeliveryService;

    @Value("${leave.monthly-days:2.0}")
    private BigDecimal monthlyLeaveDays;

    @Value("${leave.annual-days:24.0}")
    private BigDecimal annualLeaveDays;

    @Value("${wfh.monthly-days:3.0}")
    private BigDecimal monthlyWfhDays;

    @Value("${wfh.annual-days:36.0}")
    private BigDecimal annualWfhDays;

    @Value("${leave.approval-token-expiry-hours:48}")
    private int approvalTokenExpiryHours;

    @Value("${app.backend.base-url:http://localhost:8082}")
    private String backendBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public LeaveBalanceResponse getBalance(String empId) {
        int currentYear = LocalDate.now().getYear();
        BigDecimal leaveUsed = sumRequestDaysForYear(empId, LeaveRequestType.LEAVE, LeaveRequestStatus.APPROVED, currentYear);
        BigDecimal leavePending = sumRequestDaysForYear(empId, LeaveRequestType.LEAVE, LeaveRequestStatus.PENDING, currentYear);
        BigDecimal leaveAvailable = annualLeaveDays.subtract(leaveUsed).subtract(leavePending).max(BigDecimal.ZERO);

        BigDecimal wfhUsed = sumRequestDaysForYear(empId, LeaveRequestType.WFH, LeaveRequestStatus.APPROVED, currentYear);
        BigDecimal wfhPending = sumRequestDaysForYear(empId, LeaveRequestType.WFH, LeaveRequestStatus.PENDING, currentYear);
        BigDecimal wfhAvailable = annualWfhDays.subtract(wfhUsed).subtract(wfhPending).max(BigDecimal.ZERO);

        LeaveBalanceResponse response = new LeaveBalanceResponse();
        response.setAllocated(scale(annualLeaveDays));
        response.setUsed(scale(leaveUsed));
        response.setPending(scale(leavePending));
        response.setAvailable(scale(leaveAvailable));
        response.setLeaveAllocated(scale(annualLeaveDays));
        response.setLeaveUsed(scale(leaveUsed));
        response.setLeavePending(scale(leavePending));
        response.setLeaveAvailable(scale(leaveAvailable));
        response.setLeaveMonthlyLimit(scale(monthlyLeaveDays));
        response.setWfhAllocated(scale(annualWfhDays));
        response.setWfhUsed(scale(wfhUsed));
        response.setWfhPending(scale(wfhPending));
        response.setWfhAvailable(scale(wfhAvailable));
        response.setWfhMonthlyLimit(scale(monthlyWfhDays));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getMyRequests(String empId) {
        return leaveRequestRepo.findByEmployeeEmpIdOrderByCreatedAtDesc(empId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveEmployeeOptionDto> getEligibleDependencies(String empId) {
        Employee requester = employeeRepo.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found."));
        return employeeRepo
            .findByDepartmentIgnoreCaseAndRoleAndIsApprovedTrue(requester.getDepartment(), Role.USER)
            .stream()
            .filter(employee -> !employee.getEmpId().equals(empId))
            .map(this::toEmployeeOption)
            .toList();
    }

    @Override
    @Transactional
    public LeaveRequestResponse createRequest(String empId, LeaveRequestCreateRequest request) {
        Employee employee = employeeRepo.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found."));

        validateCreateRequest(employee, request);
        BigDecimal totalDays = calculateTotalDays(request.getStartDate(), request.getEndDate(), request.getDayPart());

        if (request.getRequestType() == LeaveRequestType.LEAVE) {
            validateQuota(empId, LeaveRequestType.LEAVE, request, totalDays, annualLeaveDays, monthlyLeaveDays);
        } else if (request.getRequestType() == LeaveRequestType.WFH) {
            validateQuota(empId, LeaveRequestType.WFH, request, totalDays, annualWfhDays, monthlyWfhDays);
        }

        List<LeaveRequest> overlaps = leaveRequestRepo.findOverlappingRequests(
            empId,
            ACTIVE_STATUSES,
            request.getStartDate(),
            request.getEndDate()
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("A pending or approved Leave/WFH request already overlaps with these dates.");
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setRequestType(request.getRequestType());
        leaveRequest.setDayPart(request.getDayPart() == null ? LeaveDayPart.FULL_DAY : request.getDayPart());
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setRequestDate(request.getStartDate());
        leaveRequest.setLegacyType(request.getRequestType() == LeaveRequestType.WFH ? "WFH" : "CASUAL");
        leaveRequest.setTotalDays(totalDays);
        leaveRequest.setReason(safeText(request.getReason()));
        leaveRequest.setStatus(LeaveRequestStatus.PENDING);
        leaveRequest.setRequestedAt(LocalDateTime.now());

        for (Employee dependency : resolveDependencies(employee, request.getDependencyEmpIds())) {
            LeaveRequestDependency dependencyLink = new LeaveRequestDependency();
            dependencyLink.setLeaveRequest(leaveRequest);
            dependencyLink.setEmployee(dependency);
            leaveRequest.getDependencies().add(dependencyLink);
        }

        Employee approver = resolveApprover(employee);
        leaveRequest.setApproverEmpId(approver.getEmpId());
        leaveRequest.setManagerEmpId(approver.getEmpId());
        leaveRequest.setApproverName(displayName(approver, approver.getEmpId()));
        leaveRequest.setManagerNotificationMessage(
            displayName(employee, "Employee") + " submitted a " + request.getRequestType() + " request."
        );
        leaveRequest.setManagerNotificationUnread(true);
        leaveRequest.setEmployeeNotificationMessage(
            "Your " + request.getRequestType() + " request is waiting for approval from " + displayName(approver, "the approver") + "."
        );
        leaveRequest.setEmployeeNotificationUnread(true);
        leaveRequest.setNoDepartmentLeadEscalated(!Boolean.TRUE.equals(approver.getDepartmentLead()));

        LeaveRequest saved = leaveRequestRepo.save(leaveRequest);
        createApprovalTokens(saved);
        sendApprovalRequestEmail(saved, approver);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public String approveByToken(String token) {
        LeaveApprovalToken approvalToken = resolveActionToken(token, LeaveRequestStatus.APPROVED);
        LeaveRequest request = approvalToken.getLeaveRequest();
        if (request.getStatus() == LeaveRequestStatus.APPROVED) {
            return "This Leave/WFH request is already approved.";
        }
        if (request.getStatus() == LeaveRequestStatus.REJECTED) {
            return "This Leave/WFH request was already rejected.";
        }
        consumeResolvedToken(approvalToken);

        request.setStatus(LeaveRequestStatus.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setReviewedAt(request.getDecidedAt());
        request.setReviewedBy("EMAIL_TOKEN");
        request.setEmployeeNotificationMessage("Your " + request.getRequestType() + " request was approved.");
        request.setEmployeeNotificationUnread(true);
        leaveRequestRepo.save(request);

        notifyEmployee(request, "Your " + request.getRequestType() + " request is approved", approvalEmployeeHtml(request));
        notifyDependencies(request);
        return "Leave/WFH request approved successfully.";
    }

    @Override
    @Transactional
    public String rejectByToken(String token) {
        LeaveApprovalToken approvalToken = resolveActionToken(token, LeaveRequestStatus.REJECTED);
        LeaveRequest request = approvalToken.getLeaveRequest();
        if (request.getStatus() == LeaveRequestStatus.REJECTED) {
            return "This Leave/WFH request is already rejected.";
        }
        if (request.getStatus() == LeaveRequestStatus.APPROVED) {
            return "This Leave/WFH request was already approved.";
        }
        consumeResolvedToken(approvalToken);

        request.setStatus(LeaveRequestStatus.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setReviewedAt(request.getDecidedAt());
        request.setReviewedBy("EMAIL_TOKEN");
        request.setEmployeeNotificationMessage("Your " + request.getRequestType() + " request was rejected.");
        request.setEmployeeNotificationUnread(true);
        leaveRequestRepo.save(request);

        notifyEmployee(request, "Your " + request.getRequestType() + " request is rejected", rejectionEmployeeHtml(request));
        return "Leave/WFH request rejected successfully.";
    }

    @Override
    @Transactional
    public List<LeaveRequestResponse> markNotificationsRead(String empId) {
        List<LeaveRequest> requests = leaveRequestRepo.findByEmployeeEmpIdAndStatusInOrderByCreatedAtDesc(
            empId,
            List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED)
        );
        boolean changed = false;
        for (LeaveRequest request : requests) {
            if (Boolean.TRUE.equals(request.getEmployeeNotificationUnread())) {
                request.setEmployeeNotificationUnread(false);
                changed = true;
            }
        }
        return (changed ? leaveRequestRepo.saveAll(requests) : requests).stream()
            .map(this::toResponse)
            .toList();
    }

    private void validateCreateRequest(Employee employee, LeaveRequestCreateRequest request) {
        if (request.getRequestType() == null) {
            throw new IllegalArgumentException("Request type is required.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        LocalDate today = LocalDate.now();
        if (request.getStartDate().isBefore(today) || request.getEndDate().isBefore(today)) {
            throw new IllegalArgumentException("Leave/WFH cannot be applied for past dates.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }
        LeaveDayPart dayPart = request.getDayPart() == null ? LeaveDayPart.FULL_DAY : request.getDayPart();
        if (dayPart != LeaveDayPart.FULL_DAY && !request.getStartDate().equals(request.getEndDate())) {
            throw new IllegalArgumentException("Half-day requests must be for a single date.");
        }
        String reason = safeText(request.getReason());
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required.");
        }
        if (employee.getDepartment() == null || employee.getDepartment().isBlank()) {
            throw new IllegalArgumentException("Department is required before applying Leave/WFH.");
        }
    }

    private List<Employee> resolveDependencies(Employee requester, List<String> dependencyEmpIds) {
        Set<String> uniqueIds = new LinkedHashSet<>(dependencyEmpIds == null ? List.of() : dependencyEmpIds);
        uniqueIds.removeIf(value -> value == null || value.isBlank());
        if (uniqueIds.size() > 3) {
            throw new IllegalArgumentException("You can select a maximum of 3 dependency employees.");
        }
        if (uniqueIds.contains(requester.getEmpId())) {
            throw new IllegalArgumentException("You cannot select yourself as a dependency employee.");
        }

        return uniqueIds.stream()
            .map(empId -> employeeRepo.findByEmpId(empId)
                .orElseThrow(() -> new IllegalArgumentException("Dependency employee not found: " + empId)))
            .peek(employee -> {
                if (employee.getRole() == Role.ADMIN || !Boolean.TRUE.equals(employee.getIsApproved())) {
                    throw new IllegalArgumentException("Dependency employees must be approved employees.");
                }
                if (!sameDepartment(requester, employee)) {
                    throw new IllegalArgumentException("Dependency employees must belong to your department.");
                }
            })
            .toList();
    }

    private Employee resolveApprover(Employee employee) {
        Employee departmentLead = employeeRepo
            .findFirstByDepartmentIgnoreCaseAndDepartmentLeadTrueAndIsApprovedTrue(employee.getDepartment())
            .orElse(null);
        if (departmentLead != null && !departmentLead.getEmpId().equals(employee.getEmpId())) {
            return departmentLead;
        }

        return employeeRepo.findByRole(Role.ADMIN).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No department lead or admin approver is available."));
    }

    private void createApprovalTokens(LeaveRequest request) {
        tokenRepo.save(buildToken(request, LeaveRequestStatus.APPROVED));
        tokenRepo.save(buildToken(request, LeaveRequestStatus.REJECTED));
    }

    private LeaveApprovalToken buildToken(LeaveRequest request, LeaveRequestStatus action) {
        LeaveApprovalToken token = new LeaveApprovalToken();
        token.setLeaveRequest(request);
        token.setAction(action);
        token.setToken(generateToken());
        token.setExpiresAt(LocalDateTime.now().plusHours(Math.max(1, approvalTokenExpiryHours)));
        return token;
    }

    private LeaveApprovalToken resolveActionToken(String tokenValue, LeaveRequestStatus expectedAction) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new IllegalArgumentException("Approval token is required.");
        }
        LeaveApprovalToken token = tokenRepo.findByToken(tokenValue)
            .orElseThrow(() -> new IllegalArgumentException("Approval link is invalid."));
        if (token.getAction() != expectedAction) {
            throw new IllegalArgumentException("Approval link action is invalid.");
        }
        return token;
    }

    private LeaveApprovalToken consumeResolvedToken(LeaveApprovalToken token) {
        if (Boolean.TRUE.equals(token.getUsed()) && token.getLeaveRequest().getStatus() == LeaveRequestStatus.PENDING) {
            throw new IllegalArgumentException("Approval link was already used.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now()) && token.getLeaveRequest().getStatus() == LeaveRequestStatus.PENDING) {
            throw new IllegalArgumentException("Approval link has expired.");
        }
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        return tokenRepo.save(token);
    }

    private void sendApprovalRequestEmail(LeaveRequest request, Employee approver) {
        List<LeaveApprovalToken> tokens = tokenRepo.findByLeaveRequestId(request.getId());
        String approveToken = tokens.stream()
            .filter(token -> token.getAction() == LeaveRequestStatus.APPROVED)
            .findFirst()
            .map(LeaveApprovalToken::getToken)
            .orElse("");
        String rejectToken = tokens.stream()
            .filter(token -> token.getAction() == LeaveRequestStatus.REJECTED)
            .findFirst()
            .map(LeaveApprovalToken::getToken)
            .orElse("");

        String note = Boolean.TRUE.equals(request.getNoDepartmentLeadEscalated())
            ? "<p style=\"margin:0 0 12px;color:#92400e;\"><strong>No department lead assigned, admin approval required.</strong></p>"
            : "";
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:620px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Dear %s,</p>
                %s
                <p style="margin:0 0 12px;"><strong>%s</strong> submitted a %s request.</p>
                <p style="margin:0 0 8px;"><strong>Department:</strong> %s</p>
                <p style="margin:0 0 8px;"><strong>Dates:</strong> %s to %s</p>
                <p style="margin:0 0 8px;"><strong>Duration:</strong> %s</p>
                <p style="margin:0 0 8px;"><strong>Total days:</strong> %s</p>
                <p style="margin:0 0 8px;"><strong>Reason:</strong> %s</p>
                <p style="margin:0 0 18px;"><strong>Dependency employees:</strong> %s</p>
                <p style="margin:0 0 20px;">
                  <a href="%s" style="display:inline-block;background:#166534;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;margin-right:8px;">Approve</a>
                  <a href="%s" style="display:inline-block;background:#991b1b;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">Reject</a>
                </p>
                <p style="margin:0;color:#64748b;font-size:12px;">These secure links are one-time use and expire in %s hour(s).</p>
              </div>
            </div>
            """.formatted(
                displayName(approver, "Approver"),
                note,
                displayName(request.getEmployee(), "Employee"),
                request.getRequestType(),
                request.getEmployee().getDepartment(),
                request.getStartDate(),
                request.getEndDate(),
                formatDayPart(request.getDayPart()),
                request.getTotalDays(),
                escapeHtml(request.getReason()),
                dependencyNames(request),
                leaveActionUrl("approve", approveToken),
                leaveActionUrl("reject", rejectToken),
                Math.max(1, approvalTokenExpiryHours));
        mailDeliveryService.sendHtmlEmail(approver.getEmail(), "Leave/WFH approval request", html);
    }

    private void notifyEmployee(LeaveRequest request, String subject, String html) {
        Employee employee = request.getEmployee();
        if (employee != null && employee.getEmail() != null && !employee.getEmail().isBlank()) {
            mailDeliveryService.sendHtmlEmail(employee.getEmail(), subject, html);
        }
    }

    private void notifyDependencies(LeaveRequest request) {
        for (LeaveRequestDependency dependency : request.getDependencies()) {
            Employee employee = dependency.getEmployee();
            if (employee == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
                continue;
            }
            String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong>'s %s request was approved and you were listed as a dependency employee.</p>
                    <p style="margin:0 0 8px;"><strong>Dates:</strong> %s to %s</p>
                    <p style="margin:0;"><strong>Total days:</strong> %s</p>
                  </div>
                </div>
                """.formatted(
                    displayName(employee, "Team member"),
                    displayName(request.getEmployee(), "A team member"),
                    request.getRequestType(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getTotalDays());
            mailDeliveryService.sendHtmlEmail(employee.getEmail(), "Dependency notice: approved Leave/WFH", html);
        }
    }

    private String approvalEmployeeHtml(LeaveRequest request) {
        return employeeDecisionHtml(request, "approved", "Your request has been approved.");
    }

    private String rejectionEmployeeHtml(LeaveRequest request) {
        return employeeDecisionHtml(request, "rejected", "Your request has been rejected.");
    }

    private String employeeDecisionHtml(LeaveRequest request, String status, String message) {
        return """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Dear %s,</p>
                <p style="margin:0 0 12px;">%s</p>
                <p style="margin:0 0 8px;"><strong>Request:</strong> %s</p>
                <p style="margin:0 0 8px;"><strong>Dates:</strong> %s to %s</p>
                <p style="margin:0;"><strong>Status:</strong> %s</p>
              </div>
            </div>
            """.formatted(
                displayName(request.getEmployee(), "Team member"),
                message,
                request.getRequestType(),
                request.getStartDate(),
                request.getEndDate(),
                status.toUpperCase());
    }

    private BigDecimal sumRequestDaysForYear(String empId, LeaveRequestType requestType, LeaveRequestStatus status, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        return leaveRequestRepo
            .findByEmployeeEmpIdAndRequestTypeAndStatusAndStartDateGreaterThanEqual(
                empId,
                requestType,
                status,
                yearStart
            )
            .stream()
            .filter(existing -> !existing.getEndDate().isBefore(yearStart) && !existing.getStartDate().isAfter(yearEnd))
            .map(existing -> daysInsideYear(existing, year))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateQuota(
        String empId,
        LeaveRequestType requestType,
        LeaveRequestCreateRequest request,
        BigDecimal totalDays,
        BigDecimal annualLimit,
        BigDecimal monthlyLimit
    ) {
        Map<Integer, BigDecimal> requestedByYear =
            requestedDaysByYear(request.getStartDate(), request.getEndDate(), request.getDayPart());
        for (Map.Entry<Integer, BigDecimal> entry : requestedByYear.entrySet()) {
            BigDecimal used = sumRequestDaysForYear(empId, requestType, LeaveRequestStatus.APPROVED, entry.getKey());
            BigDecimal pending = sumRequestDaysForYear(empId, requestType, LeaveRequestStatus.PENDING, entry.getKey());
            BigDecimal annualAvailable = annualLimit.subtract(used).subtract(pending).max(BigDecimal.ZERO);
            if (entry.getValue().compareTo(annualAvailable) > 0) {
                throw new IllegalArgumentException(
                    requestType + " days cannot exceed available yearly balance for " + entry.getKey() + "."
                );
            }
        }

        Map<YearMonth, BigDecimal> requestedByMonth =
            requestedDaysByMonth(request.getStartDate(), request.getEndDate(), request.getDayPart());
        for (Map.Entry<YearMonth, BigDecimal> entry : requestedByMonth.entrySet()) {
            BigDecimal activeDays = activeDaysInMonth(empId, requestType, entry.getKey());
            if (activeDays.add(entry.getValue()).compareTo(monthlyLimit) > 0) {
                throw new IllegalArgumentException(
                    requestType + " monthly limit is " + scale(monthlyLimit) + " day(s) for " + entry.getKey() + "."
                );
            }
        }
    }

    private BigDecimal activeDaysInMonth(String empId, LeaveRequestType requestType, YearMonth month) {
        LocalDate yearStart = month.atDay(1).withDayOfYear(1);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        BigDecimal total = BigDecimal.ZERO;

        for (LeaveRequestStatus status : ACTIVE_STATUSES) {
            total = total.add(leaveRequestRepo
                .findByEmployeeEmpIdAndRequestTypeAndStatusAndStartDateGreaterThanEqual(
                    empId,
                    requestType,
                    status,
                    yearStart
                )
                .stream()
                .filter(existing -> !existing.getEndDate().isBefore(monthStart) && !existing.getStartDate().isAfter(monthEnd))
                .map(existing -> daysInsideMonth(existing, month))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return total;
    }

    private Map<Integer, BigDecimal> requestedDaysByYear(LocalDate startDate, LocalDate endDate, LeaveDayPart dayPart) {
        Map<Integer, BigDecimal> totals = new LinkedHashMap<>();
        LeaveDayPart resolvedDayPart = dayPart == null ? LeaveDayPart.FULL_DAY : dayPart;
        if (resolvedDayPart != LeaveDayPart.FULL_DAY) {
            totals.put(startDate.getYear(), BigDecimal.valueOf(0.5));
            return totals;
        }

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            totals.merge(cursor.getYear(), BigDecimal.ONE, BigDecimal::add);
            cursor = cursor.plusDays(1);
        }
        return totals;
    }

    private BigDecimal daysInsideYear(LeaveRequest request, int year) {
        LeaveDayPart dayPart = request.getDayPart() == null ? LeaveDayPart.FULL_DAY : request.getDayPart();
        if (dayPart != LeaveDayPart.FULL_DAY) {
            return request.getStartDate().getYear() == year ? BigDecimal.valueOf(0.5) : BigDecimal.ZERO;
        }

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate start = request.getStartDate().isBefore(yearStart) ? yearStart : request.getStartDate();
        LocalDate end = request.getEndDate().isAfter(yearEnd) ? yearEnd : request.getEndDate();
        if (end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
    }

    private Map<YearMonth, BigDecimal> requestedDaysByMonth(LocalDate startDate, LocalDate endDate, LeaveDayPart dayPart) {
        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        LeaveDayPart resolvedDayPart = dayPart == null ? LeaveDayPart.FULL_DAY : dayPart;
        if (resolvedDayPart != LeaveDayPart.FULL_DAY) {
            totals.put(YearMonth.from(startDate), BigDecimal.valueOf(0.5));
            return totals;
        }

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            YearMonth month = YearMonth.from(cursor);
            totals.merge(month, BigDecimal.ONE, BigDecimal::add);
            cursor = cursor.plusDays(1);
        }
        return totals;
    }

    private BigDecimal daysInsideMonth(LeaveRequest request, YearMonth month) {
        LeaveDayPart dayPart = request.getDayPart() == null ? LeaveDayPart.FULL_DAY : request.getDayPart();
        if (dayPart != LeaveDayPart.FULL_DAY) {
            return YearMonth.from(request.getStartDate()).equals(month) ? BigDecimal.valueOf(0.5) : BigDecimal.ZERO;
        }

        LocalDate start = request.getStartDate().isBefore(month.atDay(1)) ? month.atDay(1) : request.getStartDate();
        LocalDate end = request.getEndDate().isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : request.getEndDate();
        if (end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);
    }

    private BigDecimal calculateTotalDays(LocalDate startDate, LocalDate endDate, LeaveDayPart dayPart) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayPart == LeaveDayPart.HALF_DAY_MORNING || dayPart == LeaveDayPart.HALF_DAY_AFTERNOON) {
            return BigDecimal.valueOf(0.5);
        }
        return BigDecimal.valueOf(days).setScale(1, RoundingMode.HALF_UP);
    }

    private LeaveRequestResponse toResponse(LeaveRequest request) {
        LeaveRequestResponse response = new LeaveRequestResponse();
        response.setId(request.getId());
        response.setRequestType(request.getRequestType());
        response.setStatus(request.getStatus());
        response.setDayPart(request.getDayPart());
        response.setStartDate(request.getStartDate());
        response.setEndDate(request.getEndDate());
        response.setTotalDays(request.getTotalDays());
        response.setReason(request.getReason());
        response.setApproverName(request.getApproverName());
        response.setNoDepartmentLeadEscalated(Boolean.TRUE.equals(request.getNoDepartmentLeadEscalated()));
        response.setEmployeeNotificationMessage(request.getEmployeeNotificationMessage());
        response.setEmployeeNotificationUnread(Boolean.TRUE.equals(request.getEmployeeNotificationUnread()));
        response.setCreatedAt(request.getCreatedAt());
        response.setDecidedAt(request.getDecidedAt());
        response.setDependencies(request.getDependencies().stream()
            .map(LeaveRequestDependency::getEmployee)
            .map(this::toEmployeeOption)
            .toList());
        return response;
    }

    private LeaveEmployeeOptionDto toEmployeeOption(Employee employee) {
        return new LeaveEmployeeOptionDto(
            employee.getEmpId(),
            employee.getName(),
            employee.getEmail(),
            employee.getDepartment(),
            employee.getDesignation()
        );
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String leaveActionUrl(String action, String token) {
        String baseUrl = backendBaseUrl == null ? "" : backendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/leave/action/" + action + "?token=" + token;
    }

    private String dependencyNames(LeaveRequest request) {
        if (request.getDependencies().isEmpty()) {
            return "None";
        }
        return request.getDependencies().stream()
            .map(LeaveRequestDependency::getEmployee)
            .map(employee -> displayName(employee, employee.getEmpId()))
            .reduce((left, right) -> left + ", " + right)
            .orElse("None");
    }

    private String formatDayPart(LeaveDayPart dayPart) {
        return switch (dayPart) {
            case HALF_DAY_MORNING -> "Half day - morning";
            case HALF_DAY_AFTERNOON -> "Half day - afternoon";
            default -> "Full day";
        };
    }

    private boolean sameDepartment(Employee left, Employee right) {
        String leftDepartment = left == null ? "" : String.valueOf(left.getDepartment()).trim();
        String rightDepartment = right == null ? "" : String.valueOf(right.getDepartment()).trim();
        return !leftDepartment.isBlank() && leftDepartment.equalsIgnoreCase(rightDepartment);
    }

    private String displayName(Employee employee, String fallback) {
        if (employee == null) {
            return fallback;
        }
        if (employee.getName() != null && !employee.getName().isBlank()) {
            return employee.getName().trim();
        }
        return fallback;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeHtml(String value) {
        return safeText(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>");
    }
}
