package com.workly.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "attendance_sessions",
    indexes = {
        @Index(name = "idx_attendance_employee_date", columnList = "employee_emp_id,sessionDate"),
        @Index(name = "idx_attendance_employee_session", columnList = "employee_emp_id,sessionKey"),
        @Index(name = "idx_attendance_active", columnList = "active,lastActivityAt")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 100)
    private String sessionKey;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private LocalDateTime clockInAt;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    private LocalDateTime clockOutAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 30)
    private String closeReason;

    @PrePersist
    public void applyDefaults() {
        if (sessionDate == null && clockInAt != null) {
            sessionDate = clockInAt.toLocalDate();
        }
        if (lastActivityAt == null) {
            lastActivityAt = clockInAt;
        }
    }
}
