package com.workly.service;

import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestDto;
import com.workly.entity.Employee;
import com.workly.entity.LeaveRequest;
import com.workly.entity.LeaveRequestStatus;
import com.workly.entity.Role;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.LeaveRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestServiceImpl.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final MailDeliveryService mailDeliveryService;

    @Value("${app.timezone:Asia/Kolkata}")
    private String appTimezone;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    @Transactional
    public LeaveRequestDto createRequest(String empId, LeaveRequestCreateRequest request) {
        Employee employee = employeeRepository.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        if (employee.getReportingManagerEmpId() == null || employee.getReportingManagerEmpId().isBlank()) {
            throw new IllegalArgumentException("A reporting manager is required before applying leave or WFH.");
        }

        Employee manager = employeeRepository.findByEmpId(employee.getReportingManagerEmpId())
            .orElseThrow(() -> new IllegalArgumentException("Reporting manager is not available."));

        LocalDate requestedDate = request.getDate();
        if (requestedDate == null) {
            throw new IllegalArgumentException("Please select a leave date.");
        }
        if (requestedDate.isBefore(currentDate())) {
            throw new IllegalArgumentException("Leave or WFH cannot be requested for past dates.");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Please select the request type.");
        }

        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5) {
            throw new IllegalArgumentException("Please provide a clear reason with at least 5 characters.");
        }
        if (reason.length() > 800) {
            throw new IllegalArgumentException("Reason must be 800 characters or less.");
        }

        leaveRequestRepository.findFirstByEmployeeEmpIdAndRequestDateAndStatusIn(
            empId,
            requestedDate,
            List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED)
        ).ifPresent(existing -> {
            throw new IllegalArgumentException("A leave or WFH request already exists for this date.");
        });

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setManagerEmpId(manager.getEmpId());
        leaveRequest.setType(request.getType());
        leaveRequest.setRequestDate(requestedDate);
        leaveRequest.setReason(reason);
        leaveRequest.setStatus(LeaveRequestStatus.PENDING);
        leaveRequest.setManagerNotificationUnread(true);
        leaveRequest.setEmployeeNotificationUnread(false);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        sendRequestMail(saved, manager);
        return toDto(saved);
    }

    @Override
    public List<LeaveRequestDto> getMyRequests(String empId) {
        return leaveRequestRepository.findByEmployeeEmpIdOrderByRequestDateDescRequestedAtDesc(empId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    public List<LeaveRequestDto> getManagedRequests(String managerEmpId) {
        Employee manager = employeeRepository.findByEmpId(managerEmpId)
            .orElseThrow(() -> new IllegalArgumentException("Manager not found"));
        if (manager.getRole() != Role.ADMIN && !Boolean.TRUE.equals(manager.getCanAssignTask())) {
            return List.of();
        }
        return leaveRequestRepository.findByManagerEmpIdOrderByRequestedAtDesc(managerEmpId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public LeaveRequestDto approveRequest(Long requestId, String reviewerEmpId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Leave request not found."));
        Employee reviewer = employeeRepository.findByEmpId(reviewerEmpId)
            .orElseThrow(() -> new IllegalArgumentException("Reviewer not found."));

        if (reviewer.getRole() != Role.ADMIN && !reviewerEmpId.equals(leaveRequest.getManagerEmpId())) {
            throw new IllegalArgumentException("Only the employee's reporting manager can approve this request.");
        }
        if (leaveRequest.getStatus() == LeaveRequestStatus.APPROVED) {
            throw new IllegalArgumentException("This request is already approved.");
        }
        if (leaveRequest.getRequestDate().isBefore(currentDate())) {
            throw new IllegalArgumentException("Past leave requests cannot be approved.");
        }

        leaveRequest.setStatus(LeaveRequestStatus.APPROVED);
        leaveRequest.setReviewedAt(LocalDateTime.now(appZone()));
        leaveRequest.setReviewedBy(reviewerEmpId);
        leaveRequest.setManagerNotificationUnread(false);
        leaveRequest.setEmployeeNotificationUnread(true);
        leaveRequest.setEmployeeNotificationMessage(
            displayName(reviewer, "Your manager") + " approved your " + typeLabel(leaveRequest) + " request for "
                + leaveRequest.getRequestDate() + "."
        );

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        sendApprovalMail(saved, reviewer);
        return toDto(saved);
    }

    @Override
    @Transactional
    public List<LeaveRequestDto> markEmployeeNotificationsRead(String empId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeEmpIdOrderByRequestDateDescRequestedAtDesc(empId);
        boolean updated = false;
        for (LeaveRequest request : requests) {
            if (Boolean.TRUE.equals(request.getEmployeeNotificationUnread())) {
                request.setEmployeeNotificationUnread(false);
                updated = true;
            }
        }
        return (updated ? leaveRequestRepository.saveAll(requests) : requests).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public List<LeaveRequestDto> markManagerNotificationsRead(String managerEmpId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByManagerEmpIdOrderByRequestedAtDesc(managerEmpId);
        boolean updated = false;
        for (LeaveRequest request : requests) {
            if (Boolean.TRUE.equals(request.getManagerNotificationUnread())) {
                request.setManagerNotificationUnread(false);
                updated = true;
            }
        }
        return (updated ? leaveRequestRepository.saveAll(requests) : requests).stream().map(this::toDto).toList();
    }

    private LeaveRequestDto toDto(LeaveRequest request) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setId(request.getId());
        dto.setEmpId(request.getEmployee().getEmpId());
        dto.setEmployeeName(request.getEmployee().getName());
        dto.setManagerEmpId(request.getManagerEmpId());
        Employee manager = employeeRepository.findByEmpId(request.getManagerEmpId()).orElse(null);
        dto.setManagerName(displayName(manager, request.getManagerEmpId()));
        dto.setType(request.getType());
        dto.setStatus(request.getStatus());
        dto.setRequestDate(request.getRequestDate());
        dto.setReason(request.getReason());
        dto.setRequestedAt(request.getRequestedAt());
        dto.setReviewedAt(request.getReviewedAt());
        dto.setReviewedBy(request.getReviewedBy());
        dto.setManagerNotificationUnread(Boolean.TRUE.equals(request.getManagerNotificationUnread()));
        dto.setEmployeeNotificationUnread(Boolean.TRUE.equals(request.getEmployeeNotificationUnread()));
        dto.setEmployeeNotificationMessage(request.getEmployeeNotificationMessage());
        return dto;
    }

    private void sendRequestMail(LeaveRequest request, Employee manager) {
        Employee requester = request.getEmployee();
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Dear %s,</p>
                <p style="margin:0 0 12px;"><strong>%s</strong> requested %s.</p>
                <p style="margin:0 0 12px;"><strong>Date:</strong> %s</p>
                <p style="margin:0 0 16px;"><strong>Reason:</strong> %s</p>
                <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">Review Request</a></p>
                <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
              </div>
            </div>
            """.formatted(
                displayName(manager, "Manager"),
                displayName(requester, "A team member"),
                typeLabel(request),
                request.getRequestDate(),
                escapeHtml(request.getReason()),
                employeeLeaveReviewsUrl()
            );
        sendMailSafely(manager, "Leave request from " + displayName(requester, "employee"), html, "leave request");
    }

    private void sendApprovalMail(LeaveRequest request, Employee reviewer) {
        Employee recipient = request.getEmployee();
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Dear %s,</p>
                <p style="margin:0 0 12px;"><strong>%s</strong> approved your %s request.</p>
                <p style="margin:0 0 16px;"><strong>Date:</strong> %s</p>
                <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">View Calendar</a></p>
                <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
              </div>
            </div>
            """.formatted(
                displayName(recipient, "Team member"),
                displayName(reviewer, "Your manager"),
                typeLabel(request),
                request.getRequestDate(),
                employeeDashboardUrl()
            );
        sendMailSafely(recipient, "Leave request approved", html, "leave approval");
    }

    private String typeLabel(LeaveRequest request) {
        return switch (request.getType()) {
            case WFH -> "work from home";
            case CASUAL -> "casual leave";
            case SICK -> "sick leave";
        };
    }

    private String employeeDashboardUrl() {
        return trimmedFrontendBaseUrl() + "/employee/dashboard";
    }

    private String employeeLeaveReviewsUrl() {
        return trimmedFrontendBaseUrl() + "/employee/tasks?section=leave-requests";
    }

    private String trimmedFrontendBaseUrl() {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void sendMailSafely(Employee recipient, String subject, String html, String context) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        try {
            mailDeliveryService.sendHtmlEmail(recipient.getEmail(), subject, html);
        } catch (Exception ex) {
            log.error("Failed to send {} email to {}", context, recipient.getEmail(), ex);
        }
    }

    private String displayName(Employee employee, String fallback) {
        if (employee != null && employee.getName() != null && !employee.getName().isBlank()) {
            return employee.getName().trim();
        }
        return fallback;
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>");
    }

    private ZoneId appZone() {
        return ZoneId.of(appTimezone);
    }

    private LocalDate currentDate() {
        return LocalDate.now(appZone());
    }
}
