package com.workly.controller;

import com.workly.dto.CreateUserRequest;
import com.workly.dto.ErrorResponse;
import com.workly.dto.ForgotPasswordRequest;
import com.workly.dto.LoginEmailRequest;
import com.workly.dto.LoginRequest;
import com.workly.dto.LoginResponse;
import com.workly.dto.ResetPasswordWithOtpRequest;
import com.workly.dto.SendPhoneOtpRequest;
import com.workly.dto.StartRegistrationRequest;
import com.workly.dto.VerifyPasswordResetOtpRequest;
import com.workly.dto.VerifyPhoneOtpRequest;
import com.workly.entity.Employee;
import com.workly.security.JwtUtil;
import com.workly.service.AttendanceService;
import com.workly.service.EmployeeService;
import com.workly.service.PasswordResetService;
import com.workly.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private RegistrationService registrationService;

    @PostMapping("/start-registration")
    public ResponseEntity<?> startRegistration(@RequestBody StartRegistrationRequest request) {
        try {
            return ResponseEntity.ok(registrationService.startRegistration(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/resend-email-verification")
    public ResponseEntity<?> resendEmailVerification(@RequestBody StartRegistrationRequest request) {
        try {
            return ResponseEntity.ok(registrationService.resendEmailVerification(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            return ResponseEntity.ok(registrationService.verifyEmail(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/send-phone-otp")
    public ResponseEntity<?> sendPhoneOtp(@RequestBody SendPhoneOtpRequest request) {
        try {
            return ResponseEntity.ok(registrationService.sendPhoneOtp(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/resend-phone-otp")
    public ResponseEntity<?> resendPhoneOtp(@RequestBody SendPhoneOtpRequest request) {
        try {
            return ResponseEntity.ok(registrationService.resendPhoneOtp(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/verify-phone-otp")
    public ResponseEntity<?> verifyPhoneOtp(@RequestBody VerifyPhoneOtpRequest request) {
        try {
            return ResponseEntity.ok(registrationService.verifyPhoneOtp(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/complete-registration")
    public ResponseEntity<?> completeRegistration(@RequestBody CreateUserRequest request) {
        try {
            return ResponseEntity.ok(registrationService.completeRegistration(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Employee employee = employeeService.findByEmpId(request.getEmpId());

        if (employee == null) {
            return ResponseEntity.badRequest().body("Employee not found");
        }

        ResponseEntity<?> statusError = validateLoginEligibility(employee);
        if (statusError != null) {
            return statusError;
        }

        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            return ResponseEntity.badRequest().body("Invalid password");
        }

        return ResponseEntity.ok(buildLoginResponse(employee));
    }

    @PostMapping("/login-email")
    public ResponseEntity<?> loginWithEmail(@RequestBody LoginEmailRequest request) {
        Employee employee = employeeService.findByEmail(request.getEmail());

        if (employee == null) {
            return ResponseEntity.badRequest().body("Employee not found");
        }

        ResponseEntity<?> statusError = validateLoginEligibility(employee);
        if (statusError != null) {
            return statusError;
        }

        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            return ResponseEntity.badRequest().body("Invalid password");
        }

        return ResponseEntity.ok(buildLoginResponse(employee));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Attendance-Session-Key", required = false) String sessionKey) {
        if (authentication != null) {
            attendanceService.handleLogout(authentication.getName(), sessionKey);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            return ResponseEntity.ok(passwordResetService.sendOtp(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<?> verifyForgotPasswordOtp(
            @Valid @RequestBody VerifyPasswordResetOtpRequest request) {
        try {
            return ResponseEntity.ok(passwordResetService.verifyOtp(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        try {
            return ResponseEntity.ok(passwordResetService.resetPassword(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    private ResponseEntity<?> validateLoginEligibility(Employee employee) {
        if (!Boolean.TRUE.equals(employee.getEmailVerified())) {
            return ResponseEntity.badRequest().body("Email verification is still pending.");
        }
        if (!Boolean.TRUE.equals(employee.getPhoneVerified())) {
            return ResponseEntity.badRequest().body("Phone verification is still pending.");
        }
        if (!Boolean.TRUE.equals(employee.getIsApproved())) {
            return ResponseEntity.badRequest().body("Your registration is waiting for admin approval.");
        }
        return null;
    }

    private LoginResponse buildLoginResponse(Employee employee) {
        LoginResponse response = new LoginResponse();
        response.setEmpId(employee.getEmpId());
        response.setName(employee.getName());
        response.setEmail(employee.getEmail());
        response.setRole(employee.getRole().name());
        response.setDepartment(employee.getDepartment());
        response.setDesignation(employee.getDesignation());
        response.setCanAssignTask(Boolean.TRUE.equals(employee.getCanAssignTask()));
        response.setToken(jwtUtil.generateToken(employee.getEmpId(), employee.getRole().name()));
        response.setPasswordResetRequired(Boolean.TRUE.equals(employee.getPasswordResetRequired()));
        return response;
    }
}
