package com.workly.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "leave_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_leave_request_employee_date_active",
        columnNames = {"employee_emp_id", "request_date", "status"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String managerEmpId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(nullable = false, length = 800)
    private String reason;

    private LocalDateTime requestedAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    private String reviewedBy;

    @Column(nullable = false)
    private Boolean managerNotificationUnread = true;

    @Column(nullable = false)
    private Boolean employeeNotificationUnread = false;

    private String employeeNotificationMessage;

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (status == null) {
            status = LeaveRequestStatus.PENDING;
        }
        if (managerNotificationUnread == null) {
            managerNotificationUnread = true;
        }
        if (employeeNotificationUnread == null) {
            employeeNotificationUnread = false;
        }
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
