package com.workly.service;

import java.util.List;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.workly.dto.CreateUserRequest;
import com.workly.dto.EmployeeProfileResponse;
import com.workly.dto.UpdateProfileRequest;
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
    private final EmailVerificationService emailVerificationService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    public Employee createEmployee(CreateUserRequest request) {
        // Validate email domain
        validateEmailDomain(request.getEmail());

        // Require email verification token
        if (request.getVerificationToken() == null || request.getVerificationToken().isBlank()) {
            throw new IllegalArgumentException("Email must be verified before creating a user.");
        }
        
        // Check if email already exists
        if (employeeRepo.existsByEmailIgnoreCase(request.getEmail())) {
            Employee existingEmployee = employeeRepo
                .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(request.getEmail())
                .orElse(null);
            if (existingEmployee != null) {
            throw new IllegalArgumentException("Email already exists. User '" + existingEmployee.getName() + "' (ID: " + existingEmployee.getEmpId() + ") is already registered with this email.");
            }
            throw new IllegalArgumentException("Email already exists.");
        }
        
        // Check if phone number already exists
        String normalizedPhone = normalizeAndValidatePhone(request.getPhoneCountryCode(), request.getPhone());
        if (employeeRepo.findByPhone(normalizedPhone).isPresent()) {
            Employee existingEmployee = employeeRepo.findByPhone(normalizedPhone).get();
            throw new IllegalArgumentException("Phone number already exists. User '" + existingEmployee.getName() + "' (ID: " + existingEmployee.getEmpId() + ") is already registered with this phone number.");
        }

        String nextEmpId = generateNextEmpId();
        String tempPasswordHash = emailVerificationService.consumeVerifiedPasswordHash(request.getEmail(), request.getVerificationToken());
        
        Employee employee = new Employee();
        employee.setEmpId(nextEmpId);
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setEmailVerified(true);
        employee.setPassword(tempPasswordHash);
        employee.setPasswordResetRequired(true);
        employee.setPhone(normalizedPhone);
        employee.setPhoneCountryCode(request.getPhoneCountryCode());
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

    private String normalizeAndValidatePhone(String phoneCountryCode, String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        if (phoneCountryCode == null || phoneCountryCode.isBlank()) {
            throw new IllegalArgumentException("Phone country code is required.");
        }

        String normalizedCountryCode = phoneCountryCode.trim();
        if (!normalizedCountryCode.startsWith("+")) {
            normalizedCountryCode = "+" + normalizedCountryCode.replaceAll("[^0-9]", "");
        }

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phone, null);
            if (!phoneUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException("Invalid phone number.");
            }

            String parsedCountryCode = "+" + parsed.getCountryCode();
            if (!parsedCountryCode.equals(normalizedCountryCode)) {
                throw new IllegalArgumentException("Phone country code does not match the phone number.");
            }

            if (parsed.getCountryCode() == 91) {
                String national = String.valueOf(parsed.getNationalNumber());
                if (!national.matches("[6-9]\\d{9}")) {
                    throw new IllegalArgumentException("India phone numbers must be 10 digits and start with 6-9.");
                }
            }

            return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }
    }

    @Override
    public Employee findByEmpId(String empId) {
        return employeeRepo.findByEmpId(empId).orElse(null);
    }

    @Override
    public Employee findByEmail(String email) {
        return employeeRepo.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email).orElse(null);
    }

    @Override
    public EmployeeProfileResponse getProfile(String empId) {
        Employee employee = employeeRepo.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        return buildProfileResponse(employee);
    }

    @Override
    public EmployeeProfileResponse updateProfile(String empId, UpdateProfileRequest request) {
        Employee employee = employeeRepo.findByEmpId(empId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        String nextName = request.getName() == null ? "" : request.getName().trim();
        String nextEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String nextPhone = request.getPhone() == null ? "" : request.getPhone().trim();

        if (nextName.isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (nextEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (nextPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }

        validateEmailDomain(nextEmail);

        Employee existingEmailUser = employeeRepo
            .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(nextEmail)
            .orElse(null);
        if (existingEmailUser != null && !existingEmailUser.getEmpId().equals(empId)) {
            throw new IllegalArgumentException("That email is already used by another account.");
        }

        String normalizedPhone = normalizeProfilePhone(employee, nextPhone);
        Employee existingPhoneUser = employeeRepo.findByPhone(normalizedPhone).orElse(null);
        if (existingPhoneUser != null && !existingPhoneUser.getEmpId().equals(empId)) {
            throw new IllegalArgumentException("That phone number is already used by another account.");
        }

        employee.setName(nextName);
        employee.setEmail(nextEmail);
        employee.setPhone(normalizedPhone);
        employeeRepo.save(employee);
        return buildProfileResponse(employee);
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
    public TaskAssignment updateProgress(Long id, int progress, String empId) {
        TaskAssignment ta = taskAssignmentRepo.findById(id).orElseThrow();

        if (!ta.getEmployee().getEmpId().equals(empId)) {
            throw new RuntimeException("You can only update progress for your own assigned tasks");
        }

        if (ta.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("This task has already been completed");
        }
        
        // If trying to complete task (100%) and submission is required, check if document is submitted
        if (progress == 100 && ta.getRequiresSubmission() && ta.getSubmissionDocPath() == null) {
            throw new RuntimeException("Document submission is required to complete this task");
        }

        if (Boolean.TRUE.equals(ta.getRequiresSubmission()) && progress == 100) {
            throw new RuntimeException("This task will be completed only after the reviewer accepts the submitted document");
        }
        
        ta.setProgress(progress);
        ta.setStatus(progress == 0 ? TaskStatus.ASSIGNED : TaskStatus.IN_PROGRESS);
        return taskAssignmentRepo.save(ta);
    }

    @Override
    @Transactional
    public List<Employee> getAllEmployees() {
        List<Employee> employees = employeeRepo.findAll();
        boolean hasUpdates = false;

        for (Employee employee : employees) {
            if (employee.getEmailVerified() == null) {
                employee.setEmailVerified(false);
                hasUpdates = true;
            }
            if (employee.getPasswordResetRequired() == null) {
                employee.setPasswordResetRequired(true);
                hasUpdates = true;
            }
        }

        if (hasUpdates) {
            employeeRepo.saveAll(employees);
        }

        return employees;
    }

    private String generateNextEmpId() {
        String maxEmpId = employeeRepo.findMaxEmpId();
        if (maxEmpId == null) {
            return "0001";
        }
        int nextId = Integer.parseInt(maxEmpId) + 1;
        return String.format("%04d", nextId);
    }

    private String normalizeProfilePhone(Employee employee, String phone) {
        if (phone.startsWith("+")) {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            try {
                Phonenumber.PhoneNumber parsed = phoneUtil.parse(phone, null);
                if (!phoneUtil.isValidNumber(parsed)) {
                    throw new IllegalArgumentException("Invalid phone number.");
                }
                employee.setPhoneCountryCode("+" + parsed.getCountryCode());
                return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            } catch (NumberParseException ex) {
                throw new IllegalArgumentException("Invalid phone number format.");
            }
        }

        return normalizeAndValidatePhone(employee.getPhoneCountryCode(), phone);
    }

    private EmployeeProfileResponse buildProfileResponse(Employee employee) {
        List<TaskAssignment> assignments = taskAssignmentRepo.findByEmployeeEmpId(employee.getEmpId());
        int totalAssigned = assignments.size();
        int totalCompleted = (int) assignments.stream()
            .filter(assignment -> assignment.getStatus() == TaskStatus.COMPLETED)
            .count();
        int averageProgress = totalAssigned == 0
            ? 0
            : (int) Math.round(assignments.stream()
                .mapToInt(assignment -> assignment.getProgress() == null ? 0 : assignment.getProgress())
                .average()
                .orElse(0));

        EmployeeProfileResponse response = new EmployeeProfileResponse();
        response.setEmpId(employee.getEmpId());
        response.setName(employee.getName());
        response.setEmail(employee.getEmail());
        response.setRole(employee.getRole().name());
        response.setPhone(employee.getPhone());
        response.setDesignation(employee.getDesignation());
        response.setCreatedAt(employee.getCreatedAt());
        response.setTotalTasksAssigned(totalAssigned);
        response.setTotalTasksCompleted(totalCompleted);
        response.setAverageProgress(averageProgress);
        return response;
    }
}
