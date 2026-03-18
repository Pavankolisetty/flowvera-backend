package com.workly.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSubmissionRequest {
    private Long taskAssignmentId;
    private String comments;
}
