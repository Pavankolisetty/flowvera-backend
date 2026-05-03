package com.workly.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AttendanceCalendarDayDto {
    private LocalDate date;
    private String status;
    private long workedMinutes;
    private LocalDateTime firstClockInAt;
    private LocalDateTime lastClockOutAt;
    private LocalDateTime lastActivityAt;
    private String holidayName;
    private boolean holiday;
    private boolean beforeJoiningDate;
    private boolean futureDate;
    private Long leaveRequestId;
    private String leaveType;
    private String leaveReason;
    private boolean workFromHome;
}
