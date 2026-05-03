package com.workly.dto;

import com.workly.entity.LeaveRequestStatus;
import com.workly.entity.LeaveRequestType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LeaveRequestDto {
    private Long id;
    private String empId;
    private String employeeName;
    private String managerEmpId;
    private String managerName;
    private LeaveRequestType type;
    private LeaveRequestStatus status;
    private LocalDate requestDate;
    private String reason;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private Boolean managerNotificationUnread;
    private Boolean employeeNotificationUnread;
    private String employeeNotificationMessage;
}
