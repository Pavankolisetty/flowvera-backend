package com.workly.entity;

import jakarta.persistence.*;
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

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true, nullable = false)
    private String phone;

    private String designation; // e.g., "Frontend Developer", "Research Engineer", "Software Developer"

    private LocalDateTime createdAt = LocalDateTime.now();
}
