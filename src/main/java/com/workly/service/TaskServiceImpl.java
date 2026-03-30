package com.workly.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.ReassignTaskRequest;
import com.workly.dto.ReviewSubmissionRequest;
import com.workly.entity.Employee;
import com.workly.entity.Role;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskProgressHistory;
import com.workly.entity.TaskStatus;
import com.workly.entity.TaskType;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.repo.TaskProgressHistoryRepository;
import com.workly.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepo;
    private final EmployeeRepository empRepo;
    private final TaskAssignmentRepository assignRepo;
    private final TaskProgressHistoryRepository taskProgressHistoryRepo;
    
    @Autowired
    private FileService fileService;

    @Override
    public Task createTask(CreateTaskRequest request, String createdBy) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setTaskType(request.getTaskType());
        task.setCreatedBy(createdBy);
        return taskRepo.save(task);
    }

    @Override
    public Task createTaskWithFile(CreateTaskWithFileRequest request, String createdBy) throws Exception {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setTaskType(request.getTaskType());
        task.setCreatedBy(createdBy);

        // Handle file upload for DOC_TEXT tasks
        if (request.getTaskType() == TaskType.DOC_TEXT && request.getDocument() != null) {
            // Create filename as taskName (will be updated when assigned to employee)
            String filePath = fileService.uploadFile(request.getDocument(), "task-docs");
            task.setDocumentPath(filePath);
        }

        return taskRepo.save(task);
    }

    @Override
    public TaskAssignment assignTask(AssignTaskRequest request, String assignedBy) {
        Task task = taskRepo.findById(request.getTaskId()).orElseThrow();
        Employee emp = empRepo.findByEmpId(request.getEmpId()).orElseThrow();
        Employee assigner = empRepo.findByEmpId(assignedBy).orElseThrow();

        if (assignedBy.equals(emp.getEmpId())) {
            throw new RuntimeException("You cannot assign a task to yourself");
        }

        if (emp.getRole() == Role.ADMIN) {
            throw new RuntimeException("Tasks can only be assigned to employee accounts");
        }

        if (assigner.getRole() != Role.ADMIN && request.getDueDate() == null) {
            throw new RuntimeException("A due date is required when delegating a task");
        }

        // Check if task is already assigned to this employee
        if (assignRepo.existsByTaskIdAndEmployeeEmpId(request.getTaskId(), request.getEmpId())) {
            throw new RuntimeException("Task is already assigned to employee: " + request.getEmpId());
        }

        TaskAssignment ta = new TaskAssignment();
        ta.setTask(task);
        ta.setEmployee(emp);
        ta.setAssignedBy(assignedBy);
        ta.setAssignedAt(LocalDateTime.now()); // Set assignment timestamp
        ta.setDueDate(request.getDueDate());
        ta.setStatus(TaskStatus.ASSIGNED);
        ta.setProgress(0);
        ta.setRequiresSubmission(request.getRequiresSubmission()); // Set whether submission is required
        ta.setSubmissionCount(0);
        ta.setAdminNotificationUnread(false);
        ta.setEmployeeNotificationUnread(false);
        ta.setEmployeeCelebrationPending(false);

        // If task has a document, create a copy with proper naming convention: taskName + empId
        if (task.getDocumentPath() != null) {
            try {
                // Create new filename: taskName + assigned + empId
                String customFileName = task.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_assigned_" + emp.getEmpId();
                
                // Copy file with custom name
                String newFilePath = fileService.copyFileWithCustomName(task.getDocumentPath(), "task-docs", customFileName);
                
                // Store the assignment-specific document path
                ta.setAssignmentDocPath(newFilePath);
                
            } catch (Exception e) {
                // If file copy fails, use original path
                ta.setAssignmentDocPath(task.getDocumentPath());
            }
        }

        TaskAssignment saved = assignRepo.save(ta);
        recordProgressHistory(saved, 0, TaskStatus.ASSIGNED, saved.getAssignedAt(), "ASSIGNED");
        return saved;
    }

    @Override
    public TaskAssignment reassignTask(ReassignTaskRequest request, String adminEmpId) {
        TaskAssignment currentAssignment = assignRepo.findById(request.getTaskAssignmentId()).orElseThrow();
        Employee newEmployee = empRepo.findByEmpId(request.getNewEmpId()).orElseThrow();

        // Update the assignment to new employee
        currentAssignment.setEmployee(newEmployee);
        currentAssignment.setAssignedBy(adminEmpId);
        currentAssignment.setAssignedAt(LocalDateTime.now()); // Update assignment timestamp
        currentAssignment.setStatus(TaskStatus.ASSIGNED);
        currentAssignment.setProgress(0);
        currentAssignment.setAdminReviewComments(null);
        currentAssignment.setReviewedAt(null);
        currentAssignment.setReviewedBy(null);
        currentAssignment.setLastSubmittedAt(null);
        currentAssignment.setSubmissionCount(0);
        currentAssignment.setAdminNotificationMessage(null);
        currentAssignment.setAdminNotificationUnread(false);
        currentAssignment.setEmployeeNotificationMessage(null);
        currentAssignment.setEmployeeNotificationUnread(false);
        currentAssignment.setEmployeeCelebrationPending(false);
        
        // Create new assignment document with new employee ID if task has a document
        if (currentAssignment.getTask().getDocumentPath() != null) {
            try {
                // Create new filename: taskName + assigned + newEmpId
                String customFileName = currentAssignment.getTask().getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_assigned_" + newEmployee.getEmpId();
                
                // Copy file with custom name for new employee
                String newFilePath = fileService.copyFileWithCustomName(currentAssignment.getTask().getDocumentPath(), "task-docs", customFileName);
                
                // Update assignment document path
                currentAssignment.setAssignmentDocPath(newFilePath);
                
            } catch (Exception e) {
                // If file copy fails, use original path
                currentAssignment.setAssignmentDocPath(currentAssignment.getTask().getDocumentPath());
            }
        }
        
        // Remove submission document as it's being reassigned
        currentAssignment.setSubmissionDocPath(null);

        TaskAssignment saved = assignRepo.save(currentAssignment);
        recordProgressHistory(saved, 0, TaskStatus.ASSIGNED, saved.getAssignedAt(), "REASSIGNED");
        return saved;
    }

    @Override
    public List<TaskAssignment> getTasksByEmpId(String empId) {
        return assignRepo.findByEmployeeEmpId(empId);
    }

    @Override
    public List<TaskAssignment> getAssignmentsCreatedBy(String assignedBy) {
        return assignRepo.findByAssignedByOrderByAssignedAtDesc(assignedBy);
    }

    @Override
    public TaskAssignment submitDocument(Long taskAssignmentId, MultipartFile file, String empId) throws Exception {
        TaskAssignment assignment = assignRepo.findById(taskAssignmentId).orElseThrow();
        
        // Verify the task belongs to this employee
        if (!assignment.getEmployee().getEmpId().equals(empId)) {
            throw new RuntimeException("Unauthorized access to task");
        }

        if (!Boolean.TRUE.equals(assignment.getRequiresSubmission())) {
            throw new RuntimeException("Document submission is not required for this task");
        }

        if (assignment.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("This task has already been accepted and completed");
        }

        // Create filename as taskName + sub + empId
        String customFileName = assignment.getTask().getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_sub_" + empId;
        
        // Upload the submission document with custom name
        String filePath = fileService.uploadFileWithCustomName(file, "submissions", customFileName);
        assignment.setSubmissionDocPath(filePath);
        assignment.setSubmissionCount((assignment.getSubmissionCount() == null ? 0 : assignment.getSubmissionCount()) + 1);
        assignment.setLastSubmittedAt(LocalDateTime.now());
        assignment.setStatus(TaskStatus.UNDER_REVIEW);
        assignment.setProgress(90);
        assignment.setAdminReviewComments(null);
        assignment.setReviewedAt(null);
        assignment.setReviewedBy(null);
        assignment.setAdminNotificationMessage(buildReviewerSubmissionNotification(assignment));
        assignment.setAdminNotificationUnread(true);
        assignment.setEmployeeNotificationMessage(
            "Your document has been submitted successfully. Please wait for the assigner to review and confirm it."
        );
        assignment.setEmployeeNotificationUnread(true);
        assignment.setEmployeeCelebrationPending(false);

        TaskAssignment saved = assignRepo.save(assignment);
        recordProgressHistory(saved, 90, TaskStatus.UNDER_REVIEW, assignment.getLastSubmittedAt(), "SUBMITTED");
        return saved;
    }

    @Override
    public TaskAssignment requestSubmissionChanges(ReviewSubmissionRequest request, String reviewerEmpId) {
        TaskAssignment assignment = assignRepo.findById(request.getTaskAssignmentId()).orElseThrow();
        String comments = request.getComments() == null ? "" : request.getComments().trim();

        validateReviewerAccess(assignment, reviewerEmpId);

        if (comments.isBlank()) {
            throw new RuntimeException("Please provide comments or improvement suggestions for the employee");
        }

        if (!Boolean.TRUE.equals(assignment.getRequiresSubmission()) || assignment.getSubmissionDocPath() == null) {
            throw new RuntimeException("No submitted document is available for review");
        }

        if (assignment.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("This task has already been accepted");
        }

        assignment.setStatus(TaskStatus.CHANGES_REQUESTED);
        assignment.setProgress(85);
        assignment.setAdminReviewComments(comments);
        assignment.setReviewedBy(reviewerEmpId);
        assignment.setReviewedAt(LocalDateTime.now());
        assignment.setAdminNotificationMessage(null);
        assignment.setAdminNotificationUnread(false);
        assignment.setEmployeeNotificationMessage(buildEmployeeChangesNotification(assignment, comments));
        assignment.setEmployeeNotificationUnread(true);
        assignment.setEmployeeCelebrationPending(false);

        TaskAssignment saved = assignRepo.save(assignment);
        recordProgressHistory(saved, 85, TaskStatus.CHANGES_REQUESTED, assignment.getReviewedAt(), "CHANGES_REQUESTED");
        return saved;
    }

    @Override
    public TaskAssignment acceptSubmission(Long taskAssignmentId, String reviewerEmpId) {
        TaskAssignment assignment = assignRepo.findById(taskAssignmentId).orElseThrow();

        validateReviewerAccess(assignment, reviewerEmpId);

        if (!Boolean.TRUE.equals(assignment.getRequiresSubmission()) || assignment.getSubmissionDocPath() == null) {
            throw new RuntimeException("No submitted document is available for acceptance");
        }

        assignment.setStatus(TaskStatus.COMPLETED);
        assignment.setProgress(100);
        assignment.setAdminReviewComments(null);
        assignment.setReviewedBy(reviewerEmpId);
        assignment.setReviewedAt(LocalDateTime.now());
        assignment.setAdminNotificationMessage(null);
        assignment.setAdminNotificationUnread(false);
        assignment.setEmployeeNotificationMessage(
            "Your submission has been accepted. This task is now marked as completed."
        );
        assignment.setEmployeeNotificationUnread(true);
        assignment.setEmployeeCelebrationPending(true);

        TaskAssignment saved = assignRepo.save(assignment);
        recordProgressHistory(saved, 100, TaskStatus.COMPLETED, assignment.getReviewedAt(), "ACCEPTED");
        return saved;
    }

    @Override
    public List<TaskAssignment> markEmployeeNotificationsRead(String empId) {
        List<TaskAssignment> assignments = assignRepo.findByEmployeeEmpId(empId);
        boolean updated = false;

        for (TaskAssignment assignment : assignments) {
            if (Boolean.TRUE.equals(assignment.getEmployeeNotificationUnread())) {
                assignment.setEmployeeNotificationUnread(false);
                if (Boolean.TRUE.equals(assignment.getEmployeeCelebrationPending())) {
                    assignment.setEmployeeCelebrationPending(false);
                }
                updated = true;
            }
        }

        return updated ? assignRepo.saveAll(assignments) : assignments;
    }

    @Override
    public List<TaskAssignment> markReviewerNotificationsRead(String reviewerEmpId) {
        List<TaskAssignment> assignments = assignRepo.findByAssignedByOrderByAssignedAtDesc(reviewerEmpId);
        boolean updated = false;

        for (TaskAssignment assignment : assignments) {
            if (Boolean.TRUE.equals(assignment.getAdminNotificationUnread())) {
                assignment.setAdminNotificationUnread(false);
                updated = true;
            }
        }

        return updated ? assignRepo.saveAll(assignments) : assignments;
    }

    private String buildReviewerSubmissionNotification(TaskAssignment assignment) {
        String employeeName = assignment.getEmployee() != null && assignment.getEmployee().getName() != null
            ? assignment.getEmployee().getName()
            : "The employee";
        String taskTitle = assignment.getTask() != null && assignment.getTask().getTitle() != null
            ? assignment.getTask().getTitle()
            : "the assigned task";

        return employeeName + " has submitted work for \"" + taskTitle
            + "\". Please review the document and confirm acceptance or share improvement notes.";
    }

    private String buildEmployeeChangesNotification(TaskAssignment assignment, String comments) {
        String taskTitle = assignment.getTask() != null && assignment.getTask().getTitle() != null
            ? assignment.getTask().getTitle()
            : "your task";

        return "Review update for \"" + taskTitle + "\": " + comments;
    }

    private void validateReviewerAccess(TaskAssignment assignment, String reviewerEmpId) {
        if (reviewerEmpId == null || reviewerEmpId.isBlank()) {
            throw new RuntimeException("Reviewer identity is missing");
        }

        if (reviewerEmpId.equals(assignment.getAssignedBy())) {
            return;
        }

        Employee reviewer = empRepo.findByEmpId(reviewerEmpId).orElseThrow();
        Employee assigner = empRepo.findByEmpId(assignment.getAssignedBy()).orElse(null);

        if (reviewer.getRole() == Role.ADMIN && assigner != null && assigner.getRole() == Role.ADMIN) {
            return;
        }

        throw new RuntimeException("Only the original assigner can review this submission");
    }

    private void recordProgressHistory(
            TaskAssignment assignment,
            int progress,
            TaskStatus status,
            LocalDateTime recordedAt,
            String source) {
        TaskProgressHistory history = new TaskProgressHistory();
        history.setTaskAssignment(assignment);
        history.setProgress(progress);
        history.setStatus(status);
        history.setRecordedAt(recordedAt == null ? LocalDateTime.now() : recordedAt);
        history.setSource(source);
        taskProgressHistoryRepo.save(history);
    }
}
