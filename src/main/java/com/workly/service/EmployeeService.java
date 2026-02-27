package com.workly.service;

import java.util.List;
import com.workly.entity.Employee;
import com.workly.entity.TaskAssignment;
import com.workly.dto.CreateUserRequest;

public interface EmployeeService {
    Employee createEmployee(CreateUserRequest request);
    Employee findByEmpId(String empId);
    Employee findByEmail(String email);
    boolean updatePassword(String empId, String oldPassword, String newPassword);
    List<TaskAssignment> viewMyTasks(String empId);
    List<TaskAssignment> viewMyActiveTasks(String empId);
    TaskAssignment updateProgress(Long assignmentId, int progress);
    List<Employee> getAllEmployees();
}

