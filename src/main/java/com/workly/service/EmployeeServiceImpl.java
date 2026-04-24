package com.workly.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.workly.dto.ApproveUserRequest;
import com.workly.dto.CreateUserRequest;
import com.workly.dto.EmployeeProfileResponse;
import com.workly.dto.UpdateProfileRequest;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskProgressHistory;
import com.workly.entity.TaskStatus;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.repo.TaskProgressHistoryRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final TaskAssignmentRepository taskAssignmentRepo;
    private final EmployeeRepository employeeRepo;
    private final TaskProgressHistoryRepository taskProgressHistoryRepo;
    private final DepartmentDirectory departmentDirectory;
    private final MailDeliveryService mailDeliveryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Employee createPendingEmployee(CreateUserRequest request) {
        validateEmailDomain(request.getEmail());

        String normalizedEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (employeeRepo.existsByEmailIgnoreCase(normalizedEmail)) {
            Employee existingEmployee = employeeRepo
                .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail)
                .orElse(null);
            if (existingEmployee != null) {
                throw new IllegalArgumentException("Email already exists. User '" + existingEmployee.getName() + "' (ID: " + existingEmployee.getEmpId() + ") is already registered with this email.");
            }
            throw new IllegalArgumentException("Email already exists.");
        }

        String normalizedPhone = normalizePhone(request.getPhone());
        Employee existingPhoneEmployee = employeeRepo.findByPhone(normalizedPhone).orElse(null);
        if (existingPhoneEmployee != null) {
            throw new IllegalArgumentException("Phone number already exists. User '" + existingPhoneEmployee.getName() + "' (ID: " + existingPhoneEmployee.getEmpId() + ") is already registered with this phone number.");
        }

        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }

        Employee employee = new Employee();
        employee.setEmpId(generatePendingEmpId());
        employee.setName(request.getName().trim());
        employee.setEmail(normalizedEmail);
        employee.setEmailVerified(true);
        employee.setPhoneVerified(true);
        employee.setIsApproved(false);
        employee.setCanAssignTask(false);
        employee.setPassword(passwordEncoder.encode(generateTemporaryPassword()));
        employee.setPasswordResetRequired(true);
        employee.setPhone(normalizedPhone);
        employee.setPhoneCountryCode(resolvePhoneCountryCode(normalizedPhone));
        employee.setRole(Role.USER);
        return employeeRepo.save(employee);
    }

    @Override
    public List<Employee> getPendingEmployees() {
        return employeeRepo.findByIsApprovedFalseAndRole(Role.USER);
    }

    @Override
    @Transactional
    public Employee approvePendingUser(String pendingEmpId, ApproveUserRequest request) {
        Employee pendingEmployee = employeeRepo.findByEmpId(pendingEmpId)
            .orElseThrow(() -> new IllegalArgumentException("Pending user not found."));

        if (pendingEmployee.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Admin accounts cannot be approved through this flow.");
        }
        if (Boolean.TRUE.equals(pendingEmployee.getIsApproved())) {
            throw new IllegalArgumentException("User is already approved.");
        }

        String department = departmentDirectory.normalizeDepartment(request.getDepartment());
        String designation = departmentDirectory.normalizeDesignation(request.getDesignation());

        if (!departmentDirectory.isValidDepartment(department)) {
            throw new IllegalArgumentException("Please select a valid department.");
        }
        if (!departmentDirectory.isValidRole(department, designation)) {
            throw new IllegalArgumentException("Please select a valid role for the chosen department.");
        }
        if (request.getCanAssignTask() == null) {
            throw new IllegalArgumentException("Please choose whether this user can assign tasks.");
        }

        String approvedEmpId = generateApprovedEmpId(department);
        String temporaryPassword = generateTemporaryPassword();

        Employee approvedEmployee = new Employee();
        approvedEmployee.setEmpId(approvedEmpId);
        approvedEmployee.setName(pendingEmployee.getName());
        approvedEmployee.setEmail(pendingEmployee.getEmail());
        approvedEmployee.setEmailVerified(true);
        approvedEmployee.setPhoneVerified(true);
        approvedEmployee.setIsApproved(true);
        approvedEmployee.setCanAssignTask(Boolean.TRUE.equals(request.getCanAssignTask()));
        approvedEmployee.setPassword(passwordEncoder.encode(temporaryPassword));
        approvedEmployee.setPasswordResetRequired(true);
        approvedEmployee.setPhone(pendingEmployee.getPhone());
        approvedEmployee.setPhoneCountryCode(resolvePhoneCountryCode(pendingEmployee.getPhone()));
        approvedEmployee.setDepartment(department);
        approvedEmployee.setDesignation(designation);
        approvedEmployee.setRole(Role.USER);
        approvedEmployee.setCreatedAt(pendingEmployee.getCreatedAt());

        employeeRepo.save(approvedEmployee);
        employeeRepo.delete(pendingEmployee);
        sendApprovalEmail(approvedEmployee, temporaryPassword);
        return approvedEmployee;
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

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phone.trim(), null);
            if (!phoneUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException("Invalid phone number.");
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
            employee.setPasswordResetRequired(false);
            employeeRepo.save(employee);
            return true;
        }
        return false;
    }

    @Override
    public List<TaskAssignment> viewMyTasks(String empId) {
        return normalizeAutoCompletedAssignments(taskAssignmentRepo.findByEmployeeEmpId(empId));
    }

    @Override
    public List<TaskAssignment> viewMyActiveTasks(String empId) {
        normalizeAutoCompletedAssignments(taskAssignmentRepo.findByEmployeeEmpId(empId));
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

        if (progress == 100 && ta.getRequiresSubmission() && ta.getSubmissionDocPath() == null) {
            throw new RuntimeException("Document submission is required to complete this task");
        }

        if (Boolean.TRUE.equals(ta.getRequiresSubmission()) && progress == 100) {
            throw new RuntimeException("This task will be completed only after the reviewer accepts the submitted document");
        }

        ta.setProgress(progress);
        if (progress >= 100 && !Boolean.TRUE.equals(ta.getRequiresSubmission())) {
            ta.setStatus(TaskStatus.COMPLETED);
            ta.setEmployeeNotificationMessage("Task marked as completed successfully.");
            ta.setEmployeeNotificationUnread(true);
            ta.setEmployeeCelebrationPending(true);
        } else {
            ta.setStatus(progress == 0 ? TaskStatus.ASSIGNED : TaskStatus.IN_PROGRESS);
        }

        TaskAssignment saved = taskAssignmentRepo.save(ta);
        recordProgressHistory(saved, saved.getProgress(), saved.getStatus(), "PROGRESS_UPDATED");
        return saved;
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
            if (employee.getPhoneVerified() == null) {
                employee.setPhoneVerified(employee.getRole() == Role.ADMIN);
                hasUpdates = true;
            }
            if (employee.getIsApproved() == null) {
                employee.setIsApproved(employee.getRole() == Role.ADMIN);
                hasUpdates = true;
            }
            if (employee.getCanAssignTask() == null) {
                employee.setCanAssignTask(employee.getRole() == Role.ADMIN);
                hasUpdates = true;
            }
            if (employee.getPasswordResetRequired() == null) {
                employee.setPasswordResetRequired(false);
                hasUpdates = true;
            }
        }

        if (hasUpdates) {
            employeeRepo.saveAll(employees);
        }

        return employees;
    }

    private String generatePendingEmpId() {
        return "PENDING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateApprovedEmpId(String department) {
        String departmentCode = departmentDirectory.getDepartmentCode(department);
        int nextSequence = employeeRepo.findByRole(Role.USER).stream()
            .map(Employee::getEmpId)
            .filter(id -> id != null && id.matches("[A-Z]{2}\\d{3,}"))
            .map(id -> id.substring(2))
            .mapToInt(Integer::parseInt)
            .max()
            .orElse(0) + 1;
        return departmentCode + String.format("%03d", nextSequence);
    }

    private String resolvePhoneCountryCode(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(phone, null);
            return "+" + parsed.getCountryCode();
        } catch (NumberParseException ex) {
            return null;
        }
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
        List<TaskAssignment> assignments = normalizeAutoCompletedAssignments(
            taskAssignmentRepo.findByEmployeeEmpId(employee.getEmpId())
        );
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
        response.setDepartment(employee.getDepartment());
        response.setDesignation(employee.getDesignation());
        response.setCanAssignTask(Boolean.TRUE.equals(employee.getCanAssignTask()));
        response.setCreatedAt(employee.getCreatedAt());
        response.setTotalTasksAssigned(totalAssigned);
        response.setTotalTasksCompleted(totalCompleted);
        response.setAverageProgress(averageProgress);
        return response;
    }

    private List<TaskAssignment> normalizeAutoCompletedAssignments(List<TaskAssignment> assignments) {
        boolean updated = false;

        for (TaskAssignment assignment : assignments) {
            if (!Boolean.TRUE.equals(assignment.getRequiresSubmission())
                    && assignment.getProgress() != null
                    && assignment.getProgress() >= 100
                    && assignment.getStatus() != TaskStatus.COMPLETED) {
                assignment.setStatus(TaskStatus.COMPLETED);
                assignment.setEmployeeNotificationMessage("Task marked as completed successfully.");
                assignment.setEmployeeNotificationUnread(true);
                assignment.setEmployeeCelebrationPending(true);
                updated = true;
            }
        }

        if (updated) {
            taskAssignmentRepo.saveAll(assignments);
        }

        return assignments;
    }

    private void recordProgressHistory(TaskAssignment assignment, Integer progress, TaskStatus status, String source) {
        TaskProgressHistory history = new TaskProgressHistory();
        history.setTaskAssignment(assignment);
        history.setProgress(progress == null ? 0 : progress);
        history.setStatus(status);
        history.setRecordedAt(java.time.LocalDateTime.now());
        history.setSource(source);
        taskProgressHistoryRepo.save(history);
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        StringBuilder builder = new StringBuilder(12);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 12; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private void sendApprovalEmail(Employee employee, String temporaryPassword) {
        String html = """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
              <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                <p style="margin:0 0 12px;">Dear %s,</p>
                <p style="margin:0 0 12px;">Your Flowvera registration has been approved by the admin team.</p>
                <p style="margin:0 0 12px;"><strong>Employee ID:</strong> %s</p>
                <p style="margin:0 0 12px;"><strong>Department:</strong> %s</p>
                <p style="margin:0 0 12px;"><strong>Role:</strong> %s</p>
                <p style="margin:0 0 16px;"><strong>Temporary Password:</strong> %s</p>
                <p style="margin:0;">Please sign in and change your password on first login.</p>
              </div>
            </div>
            """.formatted(employee.getName(), employee.getEmpId(), employee.getDepartment(), employee.getDesignation(), temporaryPassword);
        mailDeliveryService.sendHtmlEmail(employee.getEmail(), "Your Flowvera account is approved", html);
    }
}
