package com.workly.dto;

import lombok.Data;

@Data
public class AdminAttendanceEmployeeDto {
    private String empId;
    private String name;
    private String designation;
    private AttendanceDaySummaryDto today;
}
