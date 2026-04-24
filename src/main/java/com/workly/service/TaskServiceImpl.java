package com.workly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.DueDateExtensionRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepo;
    private final EmployeeRepository empRepo;
    private final TaskAssignmentRepository assignRepo;
    private final TaskProgressHistoryRepository taskProgressHistoryRepo;
    private final MailDeliveryService mailDeliveryService;
    
    @Autowired
    private FileService fileService;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    public Task createTask(CreateTaskRequest request, String createdBy) {
        assertCanAssignTasks(createdBy);
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setTaskType(request.getTaskType());
        task.setCreatedBy(createdBy);
        return taskRepo.save(task);
    }

    @Override
    public Task createTaskWithFile(CreateTaskWithFileRequest request, String createdBy) throws Exception {
        assertCanAssignTasks(createdBy);
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
        assertCanAssignTasks(assigner);

        if (assignedBy.equals(emp.getEmpId())) {
            throw new RuntimeException("You cannot assign a task to yourself");
        }

        if (emp.getRole() == Role.ADMIN) {
            throw new RuntimeException("Tasks can only be assigned to employee accounts");
        }
        if (!Boolean.TRUE.equals(emp.getIsApproved())) {
            throw new RuntimeException("Tasks can only be assigned to approved users");
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

        TaskAssignment saved = withAssignerDetails(assignRepo.save(ta));
        recordProgressHistory(saved, 0, TaskStatus.ASSIGNED, saved.getAssignedAt(), "ASSIGNED");
        sendAssignmentAcknowledgement(saved, assigner);
        return saved;
    }

    @Override
    public TaskAssignment reassignTask(ReassignTaskRequest request, String adminEmpId) {
        TaskAssignment currentAssignment = assignRepo.findById(request.getTaskAssignmentId()).orElseThrow();
        Employee newEmployee = empRepo.findByEmpId(request.getNewEmpId()).orElseThrow();
        Employee assigner = empRepo.findByEmpId(adminEmpId).orElseThrow();
        assertCanAssignTasks(assigner);

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

        TaskAssignment saved = withAssignerDetails(assignRepo.save(currentAssignment));
        recordProgressHistory(saved, 0, TaskStatus.ASSIGNED, saved.getAssignedAt(), "REASSIGNED");
        sendAssignmentAcknowledgement(saved, assigner);
        return saved;
    }

    @Override
    public List<TaskAssignment> getAllAssignments() {
        return withAssignerDetails(assignRepo.findAllByOrderByAssignedAtDesc());
    }

    @Override
    public List<TaskAssignment> getTasksByEmpId(String empId) {
        return withAssignerDetails(assignRepo.findByEmployeeEmpId(empId));
    }

    @Override
    public List<TaskAssignment> getAssignmentsCreatedBy(String assignedBy) {
        return withAssignerDetails(assignRepo.findByAssignedByOrderByAssignedAtDesc(assignedBy));
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

        TaskAssignment saved = withAssignerDetails(assignRepo.save(assignment));
        recordProgressHistory(saved, 90, TaskStatus.UNDER_REVIEW, assignment.getLastSubmittedAt(), "SUBMITTED");
        sendSubmissionAcknowledgement(saved, empId);
        return saved;
    }

    @Override
    public TaskAssignment requestDueDateExtension(DueDateExtensionRequest request, String empId) {
        TaskAssignment assignment = assignRepo.findById(request.getTaskAssignmentId()).orElseThrow();

        if (!assignment.getEmployee().getEmpId().equals(empId)) {
            throw new RuntimeException("You can request an extension only for your own assigned task");
        }

        if (assignment.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("This task is already completed");
        }

        if (request.getRequestedDueDate() == null) {
            throw new RuntimeException("Please choose the new due date you need");
        }

        if (assignment.getDueDate() != null && !request.getRequestedDueDate().isAfter(assignment.getDueDate())) {
            throw new RuntimeException("Requested due date must be after the current due date");
        }

        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isBlank()) {
            throw new RuntimeException("Please provide a reason for the due date extension");
        }

        assignment.setRequestedDueDate(request.getRequestedDueDate());
        assignment.setDueDateExtensionReason(reason);
        assignment.setDueDateExtensionRequestedAt(LocalDateTime.now());
        assignment.setDueDateExtensionPending(true);
        assignment.setAdminNotificationMessage(buildDueDateExtensionNotification(assignment));
        assignment.setAdminNotificationUnread(true);

        TaskAssignment saved = withAssignerDetails(assignRepo.save(assignment));
        sendDueDateExtensionRequestAcknowledgement(saved);
        return saved;
    }

    @Override
    public TaskAssignment approveDueDateExtension(Long taskAssignmentId, String reviewerEmpId) {
        TaskAssignment assignment = assignRepo.findById(taskAssignmentId).orElseThrow();
        validateReviewerAccess(assignment, reviewerEmpId);

        if (!Boolean.TRUE.equals(assignment.getDueDateExtensionPending()) || assignment.getRequestedDueDate() == null) {
            throw new RuntimeException("No pending due date extension request is available");
        }

        assignment.setDueDate(assignment.getRequestedDueDate());
        assignment.setRequestedDueDate(null);
        assignment.setDueDateExtensionPending(false);
        assignment.setAdminNotificationMessage(null);
        assignment.setAdminNotificationUnread(false);
        assignment.setEmployeeNotificationMessage("Your due date extension request was approved. The task due date has been updated.");
        assignment.setEmployeeNotificationUnread(true);

        TaskAssignment saved = withAssignerDetails(assignRepo.save(assignment));
        sendDueDateExtensionApprovedAcknowledgement(saved, reviewerEmpId);
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

        TaskAssignment saved = withAssignerDetails(assignRepo.save(assignment));
        recordProgressHistory(saved, 85, TaskStatus.CHANGES_REQUESTED, assignment.getReviewedAt(), "CHANGES_REQUESTED");
        sendChangesRequestedAcknowledgement(saved, reviewerEmpId, comments);
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

        TaskAssignment saved = withAssignerDetails(assignRepo.save(assignment));
        recordProgressHistory(saved, 100, TaskStatus.COMPLETED, assignment.getReviewedAt(), "ACCEPTED");
        sendAcceptanceAcknowledgement(saved, reviewerEmpId);
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

        return withAssignerDetails(updated ? assignRepo.saveAll(assignments) : assignments);
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

        return withAssignerDetails(updated ? assignRepo.saveAll(assignments) : assignments);
    }

    private List<TaskAssignment> withAssignerDetails(List<TaskAssignment> assignments) {
        return assignments.stream()
                .map(this::withAssignerDetails)
                .toList();
    }

    private TaskAssignment withAssignerDetails(TaskAssignment assignment) {
        if (assignment == null || assignment.getAssignedBy() == null || assignment.getAssignedBy().isBlank()) {
            return assignment;
        }

        Employee assigner = empRepo.findByEmpId(assignment.getAssignedBy()).orElse(null);
        if (assigner == null) {
            assignment.setAssignedByName("Unknown assigner");
            assignment.setAssignedByRole(null);
            return assignment;
        }

        assignment.setAssignedByName(displayName(assigner, assigner.getEmpId()));
        assignment.setAssignedByRole(assigner.getRole() == null ? null : assigner.getRole().name());
        return assignment;
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

    private String buildDueDateExtensionNotification(TaskAssignment assignment) {
        String employeeName = assignment.getEmployee() != null && assignment.getEmployee().getName() != null
            ? assignment.getEmployee().getName()
            : "The employee";
        return employeeName + " requested a due date extension for \"" + taskTitle(assignment)
            + "\" to " + formatDueDate(assignment.getRequestedDueDate()) + ".";
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

    private void sendAssignmentAcknowledgement(TaskAssignment assignment, Employee assigner) {
        Employee recipient = assignment.getEmployee();
        if (isAdminActor(assigner)) {
            return;
        }
        if (!isEmployeeRecipient(recipient)) {
            return;
        }

        String assignerName = displayName(assigner, "A team member");
        String taskTitle = taskTitle(assignment);
        String dueDateText = formatDueDate(assignment.getDueDate());
        String subject = taskTitle + " assigned by " + assignerName;
        String actionUrl = employeeAssignedTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> assigned you a task.</p>
                    <p style="margin:0 0 12px;"><strong>Task:</strong> %s</p>
                    <p style="margin:0 0 16px;"><strong>Due date:</strong> %s</p>
                    <p style="margin:0 0 20px;">Please open Flowvera to review the task details and start your work.</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">View Assigned Task</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(displayName(recipient, "Team member"), assignerName, taskTitle, dueDateText, actionUrl);
        sendMailSafely(recipient, subject, html, "task assignment acknowledgement");
    }

    private void sendSubmissionAcknowledgement(TaskAssignment assignment, String submitterEmpId) {
        Employee reviewer = findReviewer(assignment);
        if (!isEmployeeRecipient(reviewer)) {
            return;
        }

        Employee submitter = empRepo.findByEmpId(submitterEmpId).orElse(null);
        boolean isResubmission = (assignment.getSubmissionCount() != null ? assignment.getSubmissionCount() : 0) > 1;
        String verb = isResubmission ? "resubmitted" : "submitted";
        String submitterName = displayName(submitter, "A team member");
        String subjectPrefix = isResubmission ? "Task resubmitted by " : "Task submitted by ";
        String actionUrl = employeeReviewTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> has %s work for <strong>%s</strong>.</p>
                    <p style="margin:0 0 16px;">Please sign in to Flowvera to review the latest submission.</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">Review Submission</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(displayName(reviewer, "Team member"), submitterName, verb, taskTitle(assignment), actionUrl);
        sendMailSafely(reviewer, subjectPrefix + submitterName + ": " + taskTitle(assignment), html, "task submission acknowledgement");
    }

    private void sendChangesRequestedAcknowledgement(TaskAssignment assignment, String reviewerEmpId, String comments) {
        Employee recipient = assignment.getEmployee();
        Employee reviewer = empRepo.findByEmpId(reviewerEmpId).orElse(null);
        if (isAdminActor(reviewer)) {
            return;
        }
        if (!isEmployeeRecipient(recipient)) {
            return;
        }

        String safeComments = comments == null || comments.isBlank() ? "Please review the task and update your submission." : comments;
        String actionUrl = employeeAssignedTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> requested improvements for <strong>%s</strong>.</p>
                    <p style="margin:0 0 12px;"><strong>Comments:</strong> %s</p>
                    <p style="margin:0 0 20px;">Please review the notes in Flowvera and resubmit your work when ready.</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">View Improvement Request</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(displayName(recipient, "Team member"), displayName(reviewer, "A reviewer"), taskTitle(assignment), escapeHtml(safeComments), actionUrl);
        sendMailSafely(recipient, "Improvement requested: " + taskTitle(assignment), html, "task improvement acknowledgement");
    }

    private void sendAcceptanceAcknowledgement(TaskAssignment assignment, String reviewerEmpId) {
        Employee recipient = assignment.getEmployee();
        Employee reviewer = empRepo.findByEmpId(reviewerEmpId).orElse(null);
        if (isAdminActor(reviewer)) {
            return;
        }
        if (!isEmployeeRecipient(recipient)) {
            return;
        }

        String actionUrl = employeeAssignedTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> accepted your work for <strong>%s</strong>.</p>
                    <p style="margin:0 0 20px;">You can open Flowvera to view the completed task.</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">View Accepted Task</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(displayName(recipient, "Team member"), displayName(reviewer, "A reviewer"), taskTitle(assignment), actionUrl);
        sendMailSafely(recipient, "Task accepted: " + taskTitle(assignment), html, "task acceptance acknowledgement");
    }

    private void sendDueDateExtensionRequestAcknowledgement(TaskAssignment assignment) {
        Employee requester = assignment.getEmployee();
        Employee reviewer = findReviewer(assignment);
        if (!isEmployeeRecipient(reviewer)) {
            return;
        }

        String actionUrl = employeeReviewTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> requested a due date extension for <strong>%s</strong>.</p>
                    <p style="margin:0 0 12px;"><strong>Current due date:</strong> %s</p>
                    <p style="margin:0 0 12px;"><strong>Requested due date:</strong> %s</p>
                    <p style="margin:0 0 16px;"><strong>Reason:</strong> %s</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">Review Extension Request</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(
                    displayName(reviewer, "Team member"),
                    displayName(requester, "A team member"),
                    taskTitle(assignment),
                    formatDueDate(assignment.getDueDate()),
                    formatDueDate(assignment.getRequestedDueDate()),
                    escapeHtml(assignment.getDueDateExtensionReason()),
                    actionUrl);
        sendMailSafely(reviewer, "Due date extension requested: " + taskTitle(assignment), html, "due date extension request acknowledgement");
    }

    private void sendDueDateExtensionApprovedAcknowledgement(TaskAssignment assignment, String reviewerEmpId) {
        Employee recipient = assignment.getEmployee();
        Employee reviewer = empRepo.findByEmpId(reviewerEmpId).orElse(null);
        if (isAdminActor(reviewer) || !isEmployeeRecipient(recipient)) {
            return;
        }

        String actionUrl = employeeAssignedTaskUrl(assignment);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#111827;line-height:1.6;background:#f3f4f6;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px;border:1px solid #e5e7eb;">
                    <p style="margin:0 0 12px;">Dear %s,</p>
                    <p style="margin:0 0 12px;"><strong>%s</strong> approved your due date extension for <strong>%s</strong>.</p>
                    <p style="margin:0 0 16px;"><strong>New due date:</strong> %s</p>
                    <p style="margin:0 0 20px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:10px;">View Updated Task</a></p>
                    <p style="margin:16px 0 0;"><strong>Regards,</strong><br/><strong>Flowvera</strong></p>
                  </div>
                </div>
                """.formatted(
                    displayName(recipient, "Team member"),
                    displayName(reviewer, "A reviewer"),
                    taskTitle(assignment),
                    formatDueDate(assignment.getDueDate()),
                    actionUrl);
        sendMailSafely(recipient, "Due date extension approved: " + taskTitle(assignment), html, "due date extension approval acknowledgement");
    }

    private void sendMailSafely(Employee recipient, String subject, String html, String context) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        try {
            mailDeliveryService.sendHtmlEmail(recipient.getEmail(), subject, html);
        } catch (Exception ex) {
            log.error("Failed to send {} to {}", context, recipient.getEmail(), ex);
        }
    }

    private Employee findReviewer(TaskAssignment assignment) {
        if (assignment == null || assignment.getAssignedBy() == null || assignment.getAssignedBy().isBlank()) {
            return null;
        }
        return empRepo.findByEmpId(assignment.getAssignedBy()).orElse(null);
    }

    private boolean isEmployeeRecipient(Employee employee) {
        return employee != null
                && employee.getRole() != Role.ADMIN
                && employee.getEmail() != null
                && !employee.getEmail().isBlank();
    }

    private boolean isAdminActor(Employee employee) {
        return employee != null && employee.getRole() == Role.ADMIN;
    }

    private String displayName(Employee employee, String fallback) {
        if (employee == null) {
            return fallback;
        }
        if (employee.getName() != null && !employee.getName().isBlank()) {
            return employee.getName().trim();
        }
        if (employee.getEmpId() != null && !employee.getEmpId().isBlank()) {
            return employee.getEmpId().trim();
        }
        return fallback;
    }

    private String taskTitle(TaskAssignment assignment) {
        if (assignment != null && assignment.getTask() != null && assignment.getTask().getTitle() != null
                && !assignment.getTask().getTitle().isBlank()) {
            return assignment.getTask().getTitle().trim();
        }
        return "Assigned task";
    }

    private String formatDueDate(LocalDate dueDate) {
        return dueDate == null ? "No due date specified" : dueDate.toString();
    }

    private String employeeTasksUrl() {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/employee/tasks";
    }

    private String employeeAssignedTaskUrl(TaskAssignment assignment) {
        return employeeTasksUrl() + "?section=assigned&assignmentId=" + assignment.getId();
    }

    private String employeeReviewTaskUrl(TaskAssignment assignment) {
        return employeeTasksUrl() + "?section=reviews&assignmentId=" + assignment.getId();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br/>");
    }

    private void assertCanAssignTasks(String assignedBy) {
        Employee assigner = empRepo.findByEmpId(assignedBy).orElseThrow();
        assertCanAssignTasks(assigner);
    }

    private void assertCanAssignTasks(Employee assigner) {
        if (assigner.getRole() == Role.ADMIN) {
            return;
        }
        if (!Boolean.TRUE.equals(assigner.getIsApproved()) || !Boolean.TRUE.equals(assigner.getCanAssignTask())) {
            throw new RuntimeException("You are not allowed to assign tasks.");
        }
    }
}
