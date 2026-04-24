package com.workly.dto;

import lombok.Data;

@Data
public class ApproveUserRequest {
    private String department;
    private String designation;
    private Boolean canAssignTask;
}
