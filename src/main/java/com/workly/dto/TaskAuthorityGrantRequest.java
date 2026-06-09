package com.workly.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class TaskAuthorityGrantRequest {
    private String empId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
}
