package com.workly.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AssignTaskRequest {
    private Long taskId;
    private String empId;
    private LocalDate dueDate;
    private Boolean requiresSubmission = false; // Whether employee needs to submit document for completion
}