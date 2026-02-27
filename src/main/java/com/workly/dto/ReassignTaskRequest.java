package com.workly.dto;

import lombok.Data;

@Data
public class ReassignTaskRequest {
    private Long taskAssignmentId;
    private String newEmpId;
}