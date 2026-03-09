package com.workly.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.workly.dto.CreateUserRequest;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskStatus;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.TaskAssignmentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final TaskAssignmentRepository taskAssignmentRepo;
    private final EmployeeRepository employeeRepo;
    
    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    public Employee createEmployee(CreateUserRequest request) {
        // Validate email domain
        validateEmailDomain(request.getEmail());
        
        // Check if email already exists
        if (employeeRepo.findByEmail(request.getEmail()).isPresent()) {
            Employee existingEmployee = employeeRepo.findByEmail(request.getEmail()).get();
            throw new IllegalArgumentException("Email already exists. User '" + existingEmployee.getName() + "' (ID: " + existingEmployee.getEmpId() + ") is already registered with this email.");
        }
        
        // Check if phone number already exists
        if (employeeRepo.findByPhone(request.getPhone()).isPresent()) {
            Employee existingEmployee = employeeRepo.findByPhone(request.getPhone()).get();
            throw new IllegalArgumentException("Phone number already exists. User '" + existingEmployee.getName() + "' (ID: " + existingEmployee.getEmpId() + ") is already registered with this phone number.");
        }
        
        String nextEmpId = generateNextEmpId();
        
        Employee employee = new Employee();
        employee.setEmpId(nextEmpId);
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setPassword(passwordEncoder.encode(request.getPassword()));
        employee.setPhone(request.getPhone());
        employee.setDesignation(request.getDesignation());
        // Always set role to USER - only admin can create new users and they shouldn't create other admins
        employee.setRole(Role.USER);
        
        return employeeRepo.save(employee);
    }
    
    private void validateEmailDomain(String email) {
        String[] allowedDomains = {"gmail.com", "outlook.com", "yahoo.com", "zoho.com"};
        
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        
        for (String allowedDomain : allowedDomains) {
            if (domain.equals(allowedDomain)) {
                return;
            }
        }
        
        throw new IllegalArgumentException("Email domain not allowed. Please use Gmail, Outlook, Yahoo, or Zoho.");
    }

    @Override
    public Employee findByEmpId(String empId) {
        return employeeRepo.findByEmpId(empId).orElse(null);
    }

    @Override
    public Employee findByEmail(String email) {
        return employeeRepo.findByEmail(email).orElse(null);
    }

    @Override
    public boolean updatePassword(String empId, String oldPassword, String newPassword) {
        Employee employee = findByEmpId(empId);
        if (employee != null && passwordEncoder.matches(oldPassword, employee.getPassword())) {
            employee.setPassword(passwordEncoder.encode(newPassword));
            employeeRepo.save(employee);
            return true;
        }
        return false;
    }

    @Override
    public List<TaskAssignment> viewMyTasks(String empId) {
        return taskAssignmentRepo.findByEmployeeEmpId(empId);
    }

    @Override
    public List<TaskAssignment> viewMyActiveTasks(String empId) {
        return taskAssignmentRepo.findByEmployeeEmpIdAndStatusNot(empId, TaskStatus.COMPLETED);
    }

    @Override
    public TaskAssignment updateProgress(Long id, int progress) {
        TaskAssignment ta = taskAssignmentRepo.findById(id).orElseThrow();
        
        // If trying to complete task (100%) and submission is required, check if document is submitted
        if (progress == 100 && ta.getRequiresSubmission() && ta.getSubmissionDocPath() == null) {
            throw new RuntimeException("Document submission is required to complete this task");
        }
        
        ta.setProgress(progress);
        ta.setStatus(progress == 100 ? TaskStatus.COMPLETED : TaskStatus.IN_PROGRESS);
        return taskAssignmentRepo.save(ta);
    }

    @Override
    public List<Employee> getAllEmployees() {
        return employeeRepo.findAll();
    }

    private String generateNextEmpId() {
        String maxEmpId = employeeRepo.findMaxEmpId();
        if (maxEmpId == null) {
            return "0001";
        }
        int nextId = Integer.parseInt(maxEmpId) + 1;
        return String.format("%04d", nextId);
    }
}
