package com.workly.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceResponse {
    private BigDecimal allocated;
    private BigDecimal used;
    private BigDecimal pending;
    private BigDecimal available;
    private BigDecimal leaveAllocated;
    private BigDecimal leaveUsed;
    private BigDecimal leavePending;
    private BigDecimal leaveAvailable;
    private BigDecimal leaveMonthlyLimit;
    private BigDecimal wfhAllocated;
    private BigDecimal wfhUsed;
    private BigDecimal wfhPending;
    private BigDecimal wfhAvailable;
    private BigDecimal wfhMonthlyLimit;
}
