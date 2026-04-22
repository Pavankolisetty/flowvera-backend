package com.workly.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String empId;
    private String name;
    private String email;
    private String role;
    private String designation;
    private String token;
    private Boolean passwordResetRequired;
}
