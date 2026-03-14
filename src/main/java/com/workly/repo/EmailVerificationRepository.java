package com.workly.repo;

import com.workly.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    void deleteByEmailIgnoreCase(String email);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
