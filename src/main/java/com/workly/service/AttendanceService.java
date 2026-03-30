package com.workly.service;

import com.workly.dto.AdminAttendanceEmployeeDto;
import com.workly.dto.EmployeeAttendanceOverviewDto;
import java.time.YearMonth;
import java.util.List;

public interface AttendanceService {
    EmployeeAttendanceOverviewDto getEmployeeOverview(String empId, String sessionKey, YearMonth month);
    EmployeeAttendanceOverviewDto clockIn(String empId, String sessionKey);
    EmployeeAttendanceOverviewDto heartbeat(String empId, String sessionKey);
    EmployeeAttendanceOverviewDto clockOut(String empId, String sessionKey);
    void handleLogout(String empId, String sessionKey);
    List<AdminAttendanceEmployeeDto> getTodayAttendanceForAdmin();
    EmployeeAttendanceOverviewDto getEmployeeHistory(String empId, int days, String sessionKey, YearMonth month);
}
