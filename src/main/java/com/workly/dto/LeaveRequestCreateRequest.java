package com.workly.dto;

import com.workly.entity.LeaveDayPart;
import com.workly.entity.LeaveRequestType;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class LeaveRequestCreateRequest {
    private LeaveRequestType requestType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveDayPart dayPart;
    private String reason;
    private List<String> dependencyEmpIds;
}
