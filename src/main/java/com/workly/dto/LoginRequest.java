package com.workly.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String empId;
    private String password;
}