package com.workly.controller;

import com.workly.dto.ErrorResponse;
import com.workly.dto.ForgotPasswordRequest;
import com.workly.dto.LoginEmailRequest;
import com.workly.dto.LoginRequest;
import com.workly.dto.LoginResponse;
import com.workly.dto.ResetPasswordWithOtpRequest;
import com.workly.dto.VerifyPasswordResetOtpRequest;
import com.workly.entity.Employee;
import com.workly.security.JwtUtil;
import com.workly.service.AttendanceService;
import com.workly.service.EmployeeService;
import com.workly.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Employee employee = employeeService.findByEmpId(request.getEmpId());

        if (employee == null) {
            return ResponseEntity.badRequest().body("Employee not found");
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

    private LoginResponse buildLoginResponse(Employee employee) {
        LoginResponse response = new LoginResponse();
        response.setEmpId(employee.getEmpId());
        response.setName(employee.getName());
        response.setEmail(employee.getEmail());
        response.setRole(employee.getRole().name());
        response.setDesignation(employee.getDesignation());
        response.setToken(jwtUtil.generateToken(employee.getEmpId(), employee.getRole().name()));
        response.setPasswordResetRequired(Boolean.TRUE.equals(employee.getPasswordResetRequired()));
        return response;
    }
}
