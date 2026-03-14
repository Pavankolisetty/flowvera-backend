package com.workly.dto;

import com.workly.entity.Role;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String name;
    private String email;
    private String phone;
    private String phoneCountryCode;
    private String designation;
    private String verificationToken;
    private Role role;
}
