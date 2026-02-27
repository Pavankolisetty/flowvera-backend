package com.workly.dto;

import lombok.Data;

@Data
public class LoginEmailRequest {
    private String email;
    private String password;
}
