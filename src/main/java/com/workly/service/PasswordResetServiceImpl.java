package com.workly.service;

import com.workly.dto.ForgotPasswordRequest;
import com.workly.dto.MessageResponse;
import com.workly.dto.ResetPasswordWithOtpRequest;
import com.workly.dto.VerifyPasswordResetOtpRequest;
import com.workly.dto.VerifyPasswordResetOtpResponse;
import com.workly.entity.Employee;
import com.workly.entity.PasswordReset;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.PasswordResetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final String GENERIC_REQUEST_MESSAGE =
            "If the email is registered, a one-time password has been sent. Please check your inbox.";

    private final EmployeeRepository employeeRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final MailDeliveryService mailDeliveryService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long otpExpiryMinutes;
    private final long resetTokenExpiryMinutes;
    private final long requestCooldownSeconds;

    public PasswordResetServiceImpl(
            EmployeeRepository employeeRepository,
            PasswordResetRepository passwordResetRepository,
            MailDeliveryService mailDeliveryService,
            PasswordEncoder passwordEncoder,
            @Value("${app.password-reset.otp-expiry-minutes:10}") long otpExpiryMinutes,
            @Value("${app.password-reset.reset-token-expiry-minutes:10}") long resetTokenExpiryMinutes,
            @Value("${app.password-reset.cooldown-seconds:60}") long requestCooldownSeconds) {
        this.employeeRepository = employeeRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.mailDeliveryService = mailDeliveryService;
        this.passwordEncoder = passwordEncoder;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.resetTokenExpiryMinutes = resetTokenExpiryMinutes;
        this.requestCooldownSeconds = requestCooldownSeconds;
    }

    @Override
    @Transactional
    public MessageResponse sendOtp(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        cleanupExpiredRecords();

        Optional<Employee> employeeOptional = employeeRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (employeeOptional.isEmpty()) {
            return new MessageResponse(GENERIC_REQUEST_MESSAGE);
        }

        Optional<PasswordReset> existingReset = passwordResetRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (existingReset.isPresent()) {
            LocalDateTime nextAllowedAt = existingReset.get().getCreatedAt().plusSeconds(requestCooldownSeconds);
            if (nextAllowedAt.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("An OTP was sent recently. Please wait a minute before requesting another one.");
            }
        }

        Employee employee = employeeOptional.get();
        passwordResetRepository.deleteByEmailIgnoreCase(email);

        String otp = generateOtp();
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setEmail(email);
        passwordReset.setEmpId(employee.getEmpId());
        passwordReset.setOtpHash(passwordEncoder.encode(otp));
        passwordReset.setOtpExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        passwordResetRepository.save(passwordReset);

        sendOtpEmail(employee, otp);
        return new MessageResponse(GENERIC_REQUEST_MESSAGE);
    }

    @Override
    @Transactional
    public VerifyPasswordResetOtpResponse verifyOtp(VerifyPasswordResetOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        cleanupExpiredRecords();

        PasswordReset passwordReset = passwordResetRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("OTP not found. Please request a new OTP."));

        if (passwordReset.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetRepository.delete(passwordReset);
            throw new IllegalArgumentException("OTP has expired. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(request.getOtp().trim(), passwordReset.getOtpHash())) {
            throw new IllegalArgumentException("Invalid OTP. Please check the code and try again.");
        }

        String resetToken = UUID.randomUUID().toString();
        passwordReset.setResetToken(resetToken);
        passwordReset.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes));
        passwordResetRepository.save(passwordReset);

        return new VerifyPasswordResetOtpResponse(
                "OTP verified successfully. You can now choose a new password.",
                resetToken
        );
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordWithOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        cleanupExpiredRecords();

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }
        if (request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }

        PasswordReset passwordReset = passwordResetRepository
                .findByEmailIgnoreCaseAndResetToken(email, request.getResetToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Password reset session is invalid. Please verify the OTP again."));

        if (passwordReset.getResetTokenExpiresAt() == null || passwordReset.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetRepository.delete(passwordReset);
            throw new IllegalArgumentException("Password reset session has expired. Please request a new OTP.");
        }

        Employee employee = employeeRepository.findByEmpId(passwordReset.getEmpId())
                .orElseThrow(() -> new IllegalArgumentException("Employee account not found."));

        employee.setPassword(passwordEncoder.encode(request.getNewPassword()));
        employee.setPasswordResetRequired(false);
        employeeRepository.save(employee);
        passwordResetRepository.delete(passwordReset);

        return new MessageResponse("Your password has been successfully updated. You can now log in with your new credentials.");
    }

    private void cleanupExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        passwordResetRepository.deleteByOtpExpiresAtBeforeAndResetTokenIsNull(now);
        passwordResetRepository.deleteByResetTokenExpiresAtBefore(now);
    }

    private String generateOtp() {
        int value = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(value);
    }

    private void sendOtpEmail(Employee employee, String otp) {
        try {
            String name = employee.getName() != null && !employee.getName().isBlank() ? employee.getName().trim() : "there";
            String html = """
                    <div style="font-family:Arial,Helvetica,sans-serif; color:#111827; line-height:1.6; background:#f3f4f6; padding:24px;">
                      <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:16px; padding:28px; border:1px solid #e5e7eb;">
                        <p style="margin:0 0 12px;">Dear %s,</p>
                        <p style="margin:0 0 12px;">We received a request to reset your <strong>Flowvera</strong> password.</p>
                        <p style="margin:0 0 12px;">Use the one-time password below to continue:</p>
                        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:10px; padding:18px; margin:16px 0; text-align:center;">
                          <p style="margin:0; font-size:28px; font-weight:700; letter-spacing:6px; color:#111827;">%s</p>
                        </div>
                        <p style="margin:0 0 12px;">This OTP will expire in %d minutes.</p>
                        <p style="margin:0 0 12px;">If you did not request a password reset, you can safely ignore this email.</p>
                        <p style="margin:16px 0 0;"><strong>Best regards,</strong><br/>
                          <strong>Vyaas Technologies</strong><br/>
                          IT Support Team
                        </p>
                      </div>
                    </div>
                    """.formatted(name, otp, otpExpiryMinutes);

            mailDeliveryService.sendHtmlEmail(employee.getEmail(), "Flowvera password reset OTP", html);
        } catch (Exception ex) {
            log.error("Failed to send password reset OTP to {}", employee.getEmail(), ex);
            throw new IllegalStateException("Failed to send password reset OTP. Please try again.", ex);
        }
    }
}
