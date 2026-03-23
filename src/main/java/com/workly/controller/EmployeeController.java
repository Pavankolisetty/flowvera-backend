package com.workly.controller;

import java.util.List;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.workly.dto.EmployeeProfileResponse;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.ErrorResponse;
import com.workly.dto.ReviewSubmissionRequest;
import com.workly.dto.TaskActionResponse;
import com.workly.dto.UpdatePasswordRequest;
import com.workly.dto.UpdateProgressRequest;
// attendance DTO removed
import com.workly.entity.TaskAssignment;
import com.workly.entity.Employee;
import com.workly.entity.Task;
import com.workly.entity.TaskType;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.service.EmployeeService;
// attendance service removed
import com.workly.service.FileService;
import com.workly.service.TaskService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final TaskService taskService;
    // attendance service removed
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private TaskAssignmentRepository assignmentRepo;

    @PutMapping("/update-password")
    public ResponseEntity<String> updatePassword(@RequestBody UpdatePasswordRequest request, Authentication auth) {
        String empId = auth.getName();
        boolean updated = employeeService.updatePassword(empId, request.getOldPassword(), request.getNewPassword());
        if (updated) {
            return ResponseEntity.ok("Password updated successfully");
        } else {
            return ResponseEntity.badRequest().body("Invalid old password");
        }
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<TaskAssignment>> myTasks(Authentication auth) {
        String empId = auth.getName();
        List<TaskAssignment> tasks = employeeService.viewMyTasks(empId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/my-tasks/active")
    public ResponseEntity<List<TaskAssignment>> myActiveTasks(Authentication auth) {
        String empId = auth.getName();
        List<TaskAssignment> tasks = employeeService.viewMyActiveTasks(empId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/me")
    public ResponseEntity<EmployeeProfileResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(employeeService.getProfile(auth.getName()));
    }

    // Attendance endpoint removed — feature disabled/cleaned up

    @PutMapping("/update-progress")
    public ResponseEntity<?> updateProgress(@RequestBody UpdateProgressRequest request, Authentication auth) {
        try {
            String empId = auth.getName();
            TaskAssignment updated = employeeService.updateProgress(request.getTaskAssignmentId(), request.getProgress(), empId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = "/submit-document/{assignmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitDocument(
            @PathVariable Long assignmentId,
            @RequestParam("document") MultipartFile document,
            Authentication auth) {
        try {
            String empId = auth.getName();
            TaskAssignment assignment = taskService.submitDocument(assignmentId, document, empId);
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Your document has been submitted successfully. Please wait for the assigner's review and acceptance confirmation."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error submitting document: " + e.getMessage());
        }
    }

    @PutMapping("/notifications/read")
    public ResponseEntity<List<TaskAssignment>> markEmployeeNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(taskService.markEmployeeNotificationsRead(auth.getName()));
    }

    @GetMapping("/assignable-employees")
    public ResponseEntity<List<Employee>> getAssignableEmployees(Authentication auth) {
        String empId = auth.getName();
        List<Employee> employees = employeeService.getAllEmployees().stream()
            .filter(employee -> employee.getRole() != com.workly.entity.Role.ADMIN)
            .filter(employee -> !employee.getEmpId().equals(empId))
            .toList();
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/delegated-tasks")
    public ResponseEntity<List<TaskAssignment>> getDelegatedTasks(Authentication auth) {
        return ResponseEntity.ok(taskService.getAssignmentsCreatedBy(auth.getName()));
    }

    @PutMapping("/delegated-notifications/read")
    public ResponseEntity<List<TaskAssignment>> markDelegatedNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(taskService.markReviewerNotificationsRead(auth.getName()));
    }

    @PostMapping("/delegated/create-task")
    public ResponseEntity<?> createDelegatedTask(@RequestBody CreateTaskRequest request, Authentication auth) {
        try {
            Task task = taskService.createTask(request, auth.getName());
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping(value = "/delegated/create-task-with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDelegatedTaskWithFile(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("empId") String empId,
            @RequestParam("dueDate") String dueDate,
            @RequestParam("requiresSubmission") Boolean requiresSubmission,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) {
        try {
            CreateTaskWithFileRequest request = new CreateTaskWithFileRequest();
            request.setTitle(title);
            request.setDescription(description);
            request.setTaskType(file != null ? TaskType.DOC_TEXT : TaskType.TEXT);
            request.setDocument(file);

            Task task = taskService.createTaskWithFile(request, auth.getName());

            AssignTaskRequest assignRequest = new AssignTaskRequest();
            assignRequest.setTaskId(task.getId());
            assignRequest.setEmpId(empId);
            assignRequest.setDueDate(LocalDate.parse(dueDate));
            assignRequest.setRequiresSubmission(requiresSubmission);

            TaskAssignment assignment = taskService.assignTask(assignRequest, auth.getName());
            return ResponseEntity.ok(assignment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Error creating and assigning task: " + e.getMessage()));
        }
    }

    @PostMapping("/delegated/assign-task")
    public ResponseEntity<?> assignDelegatedTask(@RequestBody AssignTaskRequest request, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.assignTask(request, auth.getName());
            return ResponseEntity.ok(assignment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/delegated/submission/request-changes")
    public ResponseEntity<?> requestDelegatedSubmissionChanges(@RequestBody ReviewSubmissionRequest request, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.requestSubmissionChanges(request, auth.getName());
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Improvement notes shared with the assignee successfully."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/delegated/submission/accept/{assignmentId}")
    public ResponseEntity<?> acceptDelegatedSubmission(@PathVariable Long assignmentId, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.acceptSubmission(assignmentId, auth.getName());
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Submission accepted successfully. The delegated task is now completed."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/delegated/download-document/{type}/{assignmentId}")
    public ResponseEntity<ByteArrayResource> downloadDelegatedDocument(
            @PathVariable String type,
            @PathVariable Long assignmentId,
            Authentication auth) {
        try {
            String reviewerEmpId = auth.getName();
            TaskAssignment assignment = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

            if (!reviewerEmpId.equals(assignment.getAssignedBy())) {
                throw new RuntimeException("You are not allowed to access this delegated document");
            }

            String documentPath;
            switch (type.toLowerCase()) {
                case "assignment":
                    documentPath = assignment.getAssignmentDocPath() != null
                        ? assignment.getAssignmentDocPath()
                        : assignment.getTask().getDocumentPath();
                    break;
                case "submission":
                    documentPath = assignment.getSubmissionDocPath();
                    break;
                default:
                    return ResponseEntity.badRequest().build();
            }

            if (documentPath == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = fileService.downloadFile(documentPath);
            String filename = fileService.getFileName(documentPath);
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .body(new ByteArrayResource(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/download-task-doc/{assignmentId}")
    public ResponseEntity<ByteArrayResource> downloadTaskDocument(@PathVariable Long assignmentId, Authentication auth) {
        try {
            String empId = auth.getName();
            TaskAssignment assignment = assignmentRepo.findByIdAndEmployeeEmpId(assignmentId, empId)
                .orElseThrow(() -> new RuntimeException("Task not found or unauthorized"));

            // Use assignment document path (with proper naming) if available, otherwise fall back to task document
            String documentPath = assignment.getAssignmentDocPath() != null ? 
                assignment.getAssignmentDocPath() : assignment.getTask().getDocumentPath();
            
            if (documentPath == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = fileService.downloadFile(documentPath);
            String filename = fileService.getFileName(documentPath);
            
            // Determine content type based on file extension
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .body(new ByteArrayResource(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "avif":
                return "image/avif";
            case "svg":
                return "image/svg+xml";
            case "tif":
            case "tiff":
                return "image/tiff";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            default:
                return "application/octet-stream";
        }
    }
}

