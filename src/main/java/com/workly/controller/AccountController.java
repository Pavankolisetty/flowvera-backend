package com.workly.controller;

import com.workly.dto.EmployeeProfileResponse;
import com.workly.dto.ErrorResponse;
import com.workly.dto.UpdatePasswordRequest;
import com.workly.dto.UpdateProfileRequest;
import com.workly.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public ResponseEntity<EmployeeProfileResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(employeeService.getProfile(auth.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, Authentication auth) {
        try {
            return ResponseEntity.ok(employeeService.updateProfile(auth.getName(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody UpdatePasswordRequest request, Authentication auth) {
        boolean updated = employeeService.updatePassword(auth.getName(), request.getOldPassword(), request.getNewPassword());
        if (updated) {
            return ResponseEntity.ok("Password updated successfully");
        }

        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid current password"));
    }
}
