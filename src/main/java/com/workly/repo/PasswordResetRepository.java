package com.workly.repo;

import com.workly.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    Optional<PasswordReset> findByEmailIgnoreCaseAndResetToken(String email, String resetToken);
    void deleteByEmailIgnoreCase(String email);
    void deleteByOtpExpiresAtBeforeAndResetTokenIsNull(LocalDateTime time);
    void deleteByResetTokenExpiresAtBefore(LocalDateTime time);
}
