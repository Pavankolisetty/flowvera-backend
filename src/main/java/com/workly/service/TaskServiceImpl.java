package com.workly.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.ReassignTaskRequest;
import com.workly.entity.Employee;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskStatus;
import com.workly.entity.TaskType;
import com.workly.repo.EmployeeRepository;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepo;
    private final EmployeeRepository empRepo;
    private final TaskAssignmentRepository assignRepo;
    
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

        return assignRepo.save(ta);
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

        return assignRepo.save(currentAssignment);
    }

    @Override
    public List<TaskAssignment> getTasksByEmpId(String empId) {
        return assignRepo.findByEmployeeEmpId(empId);
    }

    @Override
    public TaskAssignment submitDocument(Long taskAssignmentId, MultipartFile file, String empId) throws Exception {
        TaskAssignment assignment = assignRepo.findById(taskAssignmentId).orElseThrow();
        
        // Verify the task belongs to this employee
        if (!assignment.getEmployee().getEmpId().equals(empId)) {
            throw new RuntimeException("Unauthorized access to task");
        }

        // Create filename as taskName + sub + empId
        String customFileName = assignment.getTask().getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_sub_" + empId;
        
        // Upload the submission document with custom name
        String filePath = fileService.uploadFileWithCustomName(file, "submissions", customFileName);
        assignment.setSubmissionDocPath(filePath);
        assignment.setStatus(TaskStatus.COMPLETED);
        assignment.setProgress(100);

        return assignRepo.save(assignment);
    }
}
