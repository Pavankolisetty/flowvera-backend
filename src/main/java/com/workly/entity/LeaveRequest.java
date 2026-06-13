package com.workly.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_emp_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveDayPart dayPart = LeaveDayPart.FULL_DAY;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "type", nullable = false)
    private String legacyType;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal totalDays = BigDecimal.ZERO;

    @Column(length = 1200)
    private String reason;

    private String approverEmpId;

    private String approverName;

    @Column(name = "manager_emp_id", nullable = false)
    private String managerEmpId;

    @Column(name = "manager_notification_message", length = 1200)
    private String managerNotificationMessage;

    @Column(name = "manager_notification_unread", nullable = false)
    private Boolean managerNotificationUnread = false;

    @Column(nullable = false)
    private Boolean noDepartmentLeadEscalated = false;

    @Column(length = 1200)
    private String employeeNotificationMessage;

    @Column(nullable = false)
    private Boolean employeeNotificationUnread = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;

    private LocalDateTime reviewedAt;

    private String reviewedBy;

    @OneToMany(mappedBy = "leaveRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeaveRequestDependency> dependencies = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (status == null) {
            status = LeaveRequestStatus.PENDING;
        }
        if (dayPart == null) {
            dayPart = LeaveDayPart.FULL_DAY;
        }
        if (totalDays == null) {
            totalDays = BigDecimal.ZERO;
        }
        if (requestDate == null) {
            requestDate = startDate;
        }
        if (legacyType == null || legacyType.isBlank()) {
            legacyType = requestType == LeaveRequestType.WFH ? "WFH" : "CASUAL";
        }
        if (noDepartmentLeadEscalated == null) {
            noDepartmentLeadEscalated = false;
        }
        if (employeeNotificationUnread == null) {
            employeeNotificationUnread = false;
        }
        if ((managerEmpId == null || managerEmpId.isBlank()) && approverEmpId != null) {
            managerEmpId = approverEmpId;
        }
        if (managerNotificationUnread == null) {
            managerNotificationUnread = false;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requestedAt == null) {
            requestedAt = createdAt;
        }
    }
}
