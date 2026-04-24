package com.workly.dto;

import lombok.Data;

@Data
public class SendPhoneOtpRequest {
    private String email;
    private String phone;
}
