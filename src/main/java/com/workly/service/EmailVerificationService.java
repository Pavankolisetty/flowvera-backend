package com.workly.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.workly.dto.VerifyEmailRequest;
import com.workly.dto.VerifyEmailResponse;
import com.workly.entity.EmailVerification;
import com.workly.exception.RateLimitException;
import com.workly.repo.EmailVerificationRepository;
import com.workly.repo.EmployeeRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final int TOKEN_LENGTH = 32;
    private static final String[] ALLOWED_DOMAINS = {"gmail.com", "outlook.com", "yahoo.com", "zoho.com"};

    private final JavaMailSender mailSender;
    private final EmailPolicyService emailPolicyService;
    private final EmployeeRepository employeeRepo;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long cooldownSeconds;
    private final long expiryMinutes;
    private final String mailFromName;
    private final String mailFromAddress;
    private final String mailHost;
    private final String brandLogoUrl;

    public EmailVerificationService(
            JavaMailSender mailSender,
            EmailPolicyService emailPolicyService,
            EmployeeRepository employeeRepo,
            EmailVerificationRepository emailVerificationRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.email.verification.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${app.email.verification.expiry-minutes:15}") long expiryMinutes,
            @Value("${app.mail.from-name:Workly}") String mailFromName,
            @Value("${spring.mail.username:}") String mailFromAddress,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.brand.logo-url:}") String brandLogoUrl) {
        this.mailSender = mailSender;
        this.emailPolicyService = emailPolicyService;
        this.employeeRepo = employeeRepo;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.cooldownSeconds = cooldownSeconds;
        this.expiryMinutes = expiryMinutes;
        this.mailFromName = mailFromName;
        this.mailFromAddress = mailFromAddress;
        this.mailHost = mailHost;
        this.brandLogoUrl = brandLogoUrl;
    }

    @Transactional
    public VerifyEmailResponse sendVerificationEmail(VerifyEmailRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String name = request.getName() != null ? request.getName().trim() : "";
        cleanupExpiredVerifications();

        validateEmailDomain(email);
        emailPolicyService.validateAllowedDomain(email);

        if (employeeRepo.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }

        Optional<EmailVerification> existing = emailVerificationRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            LocalDateTime nextAllowed = existing.get().getLastSentAt().plusSeconds(cooldownSeconds);
            if (nextAllowed.isAfter(LocalDateTime.now())) {
                throw new RateLimitException("Verification already sent. Please wait before retrying.");
            }
        }

        String tempPassword = generateRandomPassword();
        String token = generateRandomToken();

        sendAccountEmail(email, name, tempPassword);

        emailVerificationRepository.deleteByEmailIgnoreCase(email);

        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setVerificationToken(token);
        verification.setTempPasswordHash(passwordEncoder.encode(tempPassword));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        verification.setLastSentAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        return new VerifyEmailResponse(token, "Verification email sent successfully.");
    }

    @Transactional
    public String consumeVerifiedPasswordHash(String email, String token) {
        if (email == null || token == null) {
            throw new IllegalArgumentException("Email verification token is required.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        cleanupExpiredVerifications();

        EmailVerification verification = emailVerificationRepository
                .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail)
                .orElse(null);
        if (verification == null) {
            throw new IllegalArgumentException("Email verification not found. Please verify the email first.");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailVerificationRepository.delete(verification);
            throw new IllegalArgumentException("Email verification expired. Please verify again.");
        }

        if (!verification.getVerificationToken().equals(token)) {
            throw new IllegalArgumentException("Invalid email verification token.");
        }

        String tempPasswordHash = verification.getTempPasswordHash();
        emailVerificationRepository.delete(verification);
        return tempPasswordHash;
    }

    private void sendAccountEmail(String email, String name, String tempPassword) {
        if (mailHost == null || mailHost.isBlank()) {
            throw new IllegalStateException("Mail is not configured. Set MAIL_HOST, MAIL_USERNAME, and MAIL_PASSWORD.");
        }
        if (mailFromAddress == null || mailFromAddress.isBlank()) {
            throw new IllegalStateException("Mail sender is not configured. Set MAIL_USERNAME.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setTo(email);
            helper.setSubject("Your Flowvera account is ready");
            if (mailFromAddress != null && !mailFromAddress.isBlank()) {
                try {
                    helper.setFrom(mailFromAddress, mailFromName);
                } catch (java.io.UnsupportedEncodingException ex) {
                    helper.setFrom(mailFromAddress);
                }
            }

            String greetingName = name.isBlank() ? "there" : name;
            String logoBlock = "";
            if (brandLogoUrl != null && !brandLogoUrl.isBlank()) {
                logoBlock = """
                        <div style="text-align:center; margin:0 0 20px;">
                          <img src="%s" alt="Vyaas Technologies" style="height:56px; width:auto; display:inline-block;" />
                        </div>
                        """.formatted(brandLogoUrl);
            }

        String html = """
                    <div style="font-family:Arial,Helvetica,sans-serif; color:#111827; line-height:1.6; background:#f3f4f6; padding:24px;">
                      <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:16px; padding:28px; border:1px solid #e5e7eb;">
                        %s
                        <p style="margin:0 0 12px;">Dear %s,</p>
                        <p style="margin:0 0 12px;">Greetings from <strong>Vyaas Technologies</strong>.</p>
                        <p style="margin:0 0 12px;">
                          We are pleased to inform you that your <strong>Flowvera</strong> account has been successfully created.
                          Flowvera is our internal platform used for managing tasks, workflows, and team collaboration.
                        </p>
                        <p style="margin:0 0 12px;">You may sign in using the temporary credentials provided below:</p>
                        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:10px; padding:16px; margin:16px 0;">
                          <p style="margin:0 0 6px;"><strong>Login Email:</strong> %s</p>
                          <p style="margin:0;"><strong>Temporary Password:</strong> %s</p>
                        </div>
                        <p style="margin:0 0 12px;">For security reasons, you will be prompted to create a new password when you log in for the first time.</p>
                        <p style="margin:0 0 12px;">
                          If you did not expect this email or require any assistance accessing your account, please contact the system administrator or the IT support team.
                        </p>
                        <p style="margin:0 0 6px;">We look forward to your productive collaboration through Flowvera.</p>
                        <p style="margin:16px 0 0;"><strong>Best regards,</strong><br/>
                          <strong>Vyaas Technologies</strong><br/>
                          IT Support Team
                        </p>
                      </div>
                    </div>
                    """.formatted(logoBlock, greetingName, email, tempPassword);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            log.error("Failed to send verification email to {}", email, ex);
            throw new IllegalStateException("Failed to send verification email. Please try again.", ex);
        }
    }

    private String generateRandomPassword() {
        return generateRandomString(TEMP_PASSWORD_LENGTH);
    }

    private String generateRandomToken() {
        return generateRandomString(TOKEN_LENGTH);
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = secureRandom.nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }

    private void validateEmailDomain(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        for (String allowedDomain : ALLOWED_DOMAINS) {
            if (domain.equals(allowedDomain)) {
                return;
            }
        }
        throw new IllegalArgumentException("Email domain not allowed. Please use Gmail, Outlook, Yahoo, or Zoho.");
    }

    private void cleanupExpiredVerifications() {
        emailVerificationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
