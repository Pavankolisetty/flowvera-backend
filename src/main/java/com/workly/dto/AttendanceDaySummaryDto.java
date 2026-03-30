package com.workly.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttendanceDaySummaryDto {
    private LocalDate date;
    private String status;
    private long workedMinutes;
    private boolean currentlyWorking;
    private int activeSessions;
    private LocalDateTime firstClockInAt;
    private LocalDateTime lastClockOutAt;
    private LocalDateTime lastActivityAt;
}
