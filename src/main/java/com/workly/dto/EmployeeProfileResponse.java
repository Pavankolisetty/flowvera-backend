package com.workly.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EmployeeProfileResponse {
    private String empId;
    private String name;
    private String email;
    private String role;
    private String phone;
    private LocalDateTime createdAt;
}
