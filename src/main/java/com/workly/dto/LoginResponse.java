package com.workly.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String empId;
    private String name;
    private String email;
    private String role;
    private String department;
    private String designation;
    private Boolean canAssignTask;
    private String token;
    private Boolean passwordResetRequired;
}
