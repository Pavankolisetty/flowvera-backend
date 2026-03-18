package com.workly.dto;

import com.workly.entity.TaskAssignment;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskActionResponse {
    private TaskAssignment assignment;
    private String message;
}
