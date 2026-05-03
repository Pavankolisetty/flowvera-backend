package com.workly.dto;

import com.workly.entity.LeaveRequestType;
import java.time.LocalDate;
import lombok.Data;

@Data
public class LeaveRequestCreateRequest {
    private LocalDate date;
    private LeaveRequestType type;
    private String reason;
}
