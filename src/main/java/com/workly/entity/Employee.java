package com.workly.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Employee {

    @Id
    private String empId; // Primary key in format 0001, 0002, etc.

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private Boolean emailVerified = false;

    @Column(nullable = false)
    private Boolean phoneVerified = false;

    @Column(nullable = false)
    private Boolean isApproved = false;

    @Column(nullable = false)
    private Boolean canAssignTask = false;

    @JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true, nullable = false)
    private String phone;

    private String phoneCountryCode;

    private String department;

    private String designation; // e.g., "Frontend Developer", "Research Engineer", "Software Developer"

    @Column(nullable = false)
    private Boolean passwordResetRequired = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (emailVerified == null) {
            emailVerified = false;
        }
        if (phoneVerified == null) {
            phoneVerified = false;
        }
        if (isApproved == null) {
            isApproved = false;
        }
        if (canAssignTask == null) {
            canAssignTask = false;
        }
        if (passwordResetRequired == null) {
            passwordResetRequired = false;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
