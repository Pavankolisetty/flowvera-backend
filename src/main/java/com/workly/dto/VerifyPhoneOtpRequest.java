package com.workly.dto;

import lombok.Data;

@Data
public class VerifyPhoneOtpRequest {
    private String email;
    private String otp;
}
