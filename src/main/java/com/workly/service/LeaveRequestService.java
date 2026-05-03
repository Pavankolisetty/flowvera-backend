package com.workly.service;

import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestDto;
import java.util.List;

public interface LeaveRequestService {
    LeaveRequestDto createRequest(String empId, LeaveRequestCreateRequest request);
    List<LeaveRequestDto> getMyRequests(String empId);
    List<LeaveRequestDto> getManagedRequests(String managerEmpId);
    LeaveRequestDto approveRequest(Long requestId, String reviewerEmpId);
    List<LeaveRequestDto> markEmployeeNotificationsRead(String empId);
    List<LeaveRequestDto> markManagerNotificationsRead(String managerEmpId);
}
