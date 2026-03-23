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
    private String designation;
    private LocalDateTime createdAt;
    private Integer totalTasksAssigned;
    private Integer totalTasksCompleted;
    private Integer averageProgress;
}
