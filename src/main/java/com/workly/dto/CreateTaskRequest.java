package com.workly.dto;

import com.workly.entity.TaskType;
import lombok.Data;

@Data
public class CreateTaskRequest {
    private String title;
    private String description;
    private TaskType taskType;
    private Boolean requiresSubmission = false; // Whether employee needs to submit document for completion
}