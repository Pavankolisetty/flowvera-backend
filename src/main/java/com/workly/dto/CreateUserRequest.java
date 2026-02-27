package com.workly.dto;

import com.workly.entity.Role;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String name;
    private String email;
    private String password;
    private String phone;
    private String specialization;
    private Role role;
}