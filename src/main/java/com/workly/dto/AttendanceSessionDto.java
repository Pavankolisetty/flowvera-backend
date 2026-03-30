package com.workly.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttendanceSessionDto {
    private Long id;
    private String sessionKey;
    private LocalDateTime clockInAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime clockOutAt;
    private boolean active;
    private String closeReason;
    private long trackedMinutes;
}
