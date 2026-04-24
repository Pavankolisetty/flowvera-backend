package com.workly.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.workly.dto.CreateUserRequest;
import com.workly.dto.SendPhoneOtpRequest;
import com.workly.dto.StartRegistrationRequest;
import com.workly.dto.VerifyPhoneOtpRequest;
import com.workly.entity.EmailVerification;
import com.workly.repo.EmailVerificationRepository;
import com.workly.repo.EmployeeRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final String[] ALLOWED_DOMAINS = {"gmail.com", "outlook.com", "yahoo.com", "zoho.com"};

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailPolicyService emailPolicyService;
    private final MailDeliveryService mailDeliveryService;
    private final EmployeeService employeeService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.registration.email-expiry-minutes:30}")
    private long emailExpiryMinutes;

    @Value("${app.registration.email-cooldown-seconds:60}")
    private long emailCooldownSeconds;

    @Value("${app.registration.otp-expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.registration.otp-cooldown-seconds:60}")
    private long otpCooldownSeconds;

    @Value("${app.registration.otp-max-attempts:5}")
    private int otpMaxAttempts;

    @Transactional
    public Map<String, Object> startRegistration(StartRegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());
        cleanupExpiredEmailTokens();
        validateEmail(email);

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }

        EmailVerification existing = emailVerificationRepository
            .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .orElse(null);

        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getEmailVerified())) {
                throw new IllegalArgumentException("Registration already started for this email. Use resend verification if needed.");
            }
            LocalDateTime nextAllowedAt = existing.getLastSentAt().plusSeconds(emailCooldownSeconds);
            if (nextAllowedAt.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Please wait before requesting another verification email.");
            }
            emailVerificationRepository.delete(existing);
        }

        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setVerificationToken(generateToken(48));
        verification.setEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(emailExpiryMinutes));
        verification.setLastSentAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        sendRegistrationEmail(verification);
        return message("Check your email to verify");
    }

    @Transactional
    public Map<String, Object> resendEmailVerification(StartRegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());
        EmailVerification verification = emailVerificationRepository
            .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .orElseThrow(() -> new IllegalArgumentException("Registration not found for this email."));

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }
        if (Boolean.TRUE.equals(verification.getEmailVerified())) {
            throw new IllegalArgumentException("Email is already verified.");
        }
        if (verification.getLastSentAt().plusSeconds(emailCooldownSeconds).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Please wait before requesting another verification email.");
        }

        verification.setVerificationToken(generateToken(48));
        verification.setEmailTokenExpiresAt(LocalDateTime.now().plusMinutes(emailExpiryMinutes));
        verification.setLastSentAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        sendRegistrationEmail(verification);
        return message("Check your email to verify");
    }

    @Transactional
    public Map<String, Object> verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required.");
        }

        EmailVerification verification = emailVerificationRepository
            .findTopByVerificationTokenOrderByCreatedAtDesc(token.trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email verification token."));

        if (Boolean.TRUE.equals(verification.getEmailVerified())) {
            Map<String, Object> response = message("Email already verified");
            response.put("email", verification.getEmail());
            return response;
        }

        if (verification.getEmailTokenExpiresAt() == null || verification.getEmailTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Email verification link has expired.");
        }

        verification.setEmailVerified(true);
        verification.setEmailVerifiedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);

        Map<String, Object> response = message("Email verified successfully");
        response.put("email", verification.getEmail());
        return response;
    }

    @Transactional
    public Map<String, Object> sendPhoneOtp(SendPhoneOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());

        EmailVerification verification = getActiveRegistration(email);
        ensureEmailVerified(verification);

        if (employeeRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists.");
        }
        if (verification.getPhoneOtpLastSentAt() != null
                && verification.getPhoneOtpLastSentAt().plusSeconds(otpCooldownSeconds).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Please wait before requesting another OTP.");
        }

        String otp = generateOtp();
        verification.setPhone(phone);
        verification.setPhoneOtp(otp);
        verification.setPhoneOtpExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        verification.setPhoneOtpLastSentAt(LocalDateTime.now());
        verification.setPhoneOtpFailedAttempts(0);
        verification.setPhoneVerified(false);
        emailVerificationRepository.save(verification);

        log.info("Simulated phone OTP for {} on {} is {}", email, phone, otp);
        Map<String, Object> response = message("OTP sent successfully");
        response.put("debugOtp", otp);
        return response;
    }

    @Transactional
    public Map<String, Object> resendPhoneOtp(SendPhoneOtpRequest request) {
        return sendPhoneOtp(request);
    }

    @Transactional
    public Map<String, Object> verifyPhoneOtp(VerifyPhoneOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        String otp = request.getOtp() == null ? "" : request.getOtp().trim();

        EmailVerification verification = getActiveRegistration(email);
        ensureEmailVerified(verification);

        if (verification.getPhone() == null || verification.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone OTP has not been requested yet.");
        }
        if (verification.getPhoneOtp() == null || verification.getPhoneOtp().isBlank()) {
            throw new IllegalArgumentException("Phone OTP has not been requested yet.");
        }
        if (verification.getPhoneOtpExpiresAt() == null || verification.getPhoneOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP expired. Please request a new OTP.");
        }
        if ((verification.getPhoneOtpFailedAttempts() == null ? 0 : verification.getPhoneOtpFailedAttempts()) >= otpMaxAttempts) {
            throw new IllegalArgumentException("OTP retry limit reached. Please request a new OTP.");
        }
        if (!verification.getPhoneOtp().equals(otp)) {
            verification.setPhoneOtpFailedAttempts((verification.getPhoneOtpFailedAttempts() == null ? 0 : verification.getPhoneOtpFailedAttempts()) + 1);
            emailVerificationRepository.save(verification);
            throw new IllegalArgumentException("Incorrect OTP.");
        }

        verification.setPhoneVerified(true);
        verification.setPhoneOtp(null);
        verification.setPhoneOtpExpiresAt(null);
        verification.setPhoneOtpFailedAttempts(0);
        emailVerificationRepository.save(verification);

        return message("Phone verified");
    }

    @Transactional
    public Map<String, Object> completeRegistration(CreateUserRequest request) {
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());

        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }

        EmailVerification verification = getActiveRegistration(email);
        ensureEmailVerified(verification);

        if (!Boolean.TRUE.equals(verification.getPhoneVerified())) {
            throw new IllegalArgumentException("Phone must be verified before completing registration.");
        }
        if (!phone.equals(verification.getPhone())) {
            throw new IllegalArgumentException("Phone does not match the verified number.");
        }
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }
        if (employeeRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists.");
        }

        CreateUserRequest createRequest = new CreateUserRequest();
        createRequest.setName(request.getName().trim());
        createRequest.setEmail(email);
        createRequest.setPhone(phone);
        employeeService.createPendingEmployee(createRequest);
        emailVerificationRepository.delete(verification);

        return message("Registration successful. Waiting for admin approval");
    }

    private EmailVerification getActiveRegistration(String email) {
        return emailVerificationRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .orElseThrow(() -> new IllegalArgumentException("Registration not found for this email."));
    }

    private void ensureEmailVerified(EmailVerification verification) {
        if (!Boolean.TRUE.equals(verification.getEmailVerified())) {
            throw new IllegalArgumentException("Email must be verified before proceeding.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        boolean allowed = false;
        for (String candidate : ALLOWED_DOMAINS) {
            if (candidate.equals(domain)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException("Email domain not allowed. Please use Gmail, Outlook, Yahoo, or Zoho.");
        }
        emailPolicyService.validateAllowedDomain(email);
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phone.trim(), null);
            if (!phoneUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException("Invalid phone number.");
            }
            if (parsed.getCountryCode() == 91) {
                String national = String.valueOf(parsed.getNationalNumber());
                if (!national.matches("[6-9]\\d{9}")) {
                    throw new IllegalArgumentException("India phone numbers must be 10 digits and start with 6-9.");
                }
            }
            return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }
    }

    private void sendRegistrationEmail(EmailVerification verification) {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String actionUrl = baseUrl + "/auth/verify-email?token=" + verification.getVerificationToken() + "&email=" + verification.getEmail();
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Hello,</p>
                <p style="margin:0 0 12px;">Please verify your email address to continue your Flowvera registration.</p>
                <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">Verify Email</a></p>
                <p style="margin:0;">If you did not request this, you can ignore this email.</p>
              </div>
            </div>
            """.formatted(actionUrl);
        mailDeliveryService.sendHtmlEmail(verification.getEmail(), "Verify your Flowvera registration", html);
    }

    private String generateToken(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private void cleanupExpiredEmailTokens() {
        emailVerificationRepository.deleteByEmailTokenExpiresAtBeforeAndEmailVerifiedFalse(LocalDateTime.now());
    }

    private Map<String, Object> message(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return response;
    }
}
