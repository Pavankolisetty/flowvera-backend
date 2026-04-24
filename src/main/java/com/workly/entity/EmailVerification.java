package com.workly.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 255)
    private String verificationToken;

    @Column(nullable = false)
    private LocalDateTime emailTokenExpiresAt;

    @Column(nullable = false)
    private Boolean emailVerified = false;

    private LocalDateTime emailVerifiedAt;

    @Column(nullable = false)
    private LocalDateTime lastSentAt;

    private String phone;

    @Column(nullable = false)
    private Boolean phoneVerified = false;

    @Column(length = 10)
    private String phoneOtp;

    private LocalDateTime phoneOtpExpiresAt;

    private LocalDateTime phoneOtpLastSentAt;

    @Column(nullable = false)
    private Integer phoneOtpFailedAttempts = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastSentAt == null) {
            lastSentAt = LocalDateTime.now();
        }
        if (emailVerified == null) {
            emailVerified = false;
        }
        if (phoneVerified == null) {
            phoneVerified = false;
        }
        if (phoneOtpFailedAttempts == null) {
            phoneOtpFailedAttempts = 0;
        }
    }
}
