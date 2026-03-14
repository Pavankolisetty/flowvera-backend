package com.workly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyPasswordResetOtpResponse {
    private String message;
    private String resetToken;
}
