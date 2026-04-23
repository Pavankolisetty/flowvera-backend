package com.workly.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DueDateExtensionRequest {
    private Long taskAssignmentId;
    private LocalDate requestedDueDate;
    private String reason;
}
