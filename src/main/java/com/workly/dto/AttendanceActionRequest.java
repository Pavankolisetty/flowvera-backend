package com.workly.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttendanceActionRequest {
    @NotBlank
    private String sessionKey;
}
