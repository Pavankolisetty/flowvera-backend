package com.workly.dto;

import lombok.Data;

@Data
public class UpdateProgressRequest {
    private Long taskAssignmentId;
    private Integer progress;
}