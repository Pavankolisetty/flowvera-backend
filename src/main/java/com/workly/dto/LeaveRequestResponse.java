package com.workly.dto;

import com.workly.entity.LeaveDayPart;
import com.workly.entity.LeaveRequestStatus;
import com.workly.entity.LeaveRequestType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LeaveRequestResponse {
    private Long id;
    private LeaveRequestType requestType;
    private LeaveRequestStatus status;
    private LeaveDayPart dayPart;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalDays;
    private String reason;
    private String approverName;
    private Boolean noDepartmentLeadEscalated;
    private String employeeNotificationMessage;
    private Boolean employeeNotificationUnread;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private List<LeaveEmployeeOptionDto> dependencies;
}
