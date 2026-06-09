package com.workly.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeProfileResponse {
    private String empId;
    private String name;
    private String email;
    private String role;
    private String phone;
    private String department;
    private String designation;
    private Boolean canAssignTask;
    private Boolean departmentLead;
    private LocalDate taskAuthorityStartDate;
    private LocalDate taskAuthorityEndDate;
    private String taskAuthorityGrantedBy;
    private String taskAuthorityReason;
    private LocalDateTime createdAt;
    private Integer totalTasksAssigned;
    private Integer totalTasksCompleted;
    private Integer averageProgress;
}
