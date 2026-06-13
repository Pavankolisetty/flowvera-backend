package com.workly.service;

import com.workly.dto.LeaveBalanceResponse;
import com.workly.dto.LeaveEmployeeOptionDto;
import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestResponse;
import java.util.List;

public interface LeaveRequestService {
    LeaveBalanceResponse getBalance(String empId);
    List<LeaveRequestResponse> getMyRequests(String empId);
    List<LeaveEmployeeOptionDto> getEligibleDependencies(String empId);
    LeaveRequestResponse createRequest(String empId, LeaveRequestCreateRequest request);
    String approveByToken(String token);
    String rejectByToken(String token);
    List<LeaveRequestResponse> markNotificationsRead(String empId);
}
