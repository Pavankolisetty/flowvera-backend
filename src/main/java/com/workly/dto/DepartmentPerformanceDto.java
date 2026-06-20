package com.workly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DepartmentPerformanceDto {
    private String empId;
    private String name;
    private String department;
    private String designation;
    private boolean currentUser;
    private boolean departmentLead;
    private long taskCount;
    private int averageProgress;
    private String message;
}
