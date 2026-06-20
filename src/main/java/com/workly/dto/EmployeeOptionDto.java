package com.workly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmployeeOptionDto {
    private String empId;
    private String name;
    private String department;
    private String role;
}
