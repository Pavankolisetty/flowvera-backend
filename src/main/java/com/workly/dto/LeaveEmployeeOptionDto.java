package com.workly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveEmployeeOptionDto {
    private String empId;
    private String name;
    private String email;
    private String department;
    private String designation;
}
