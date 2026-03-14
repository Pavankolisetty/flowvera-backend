package com.workly.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.CreateUserRequest;
import com.workly.dto.ErrorResponse;
import com.workly.dto.ReassignTaskRequest;
import com.workly.dto.VerifyEmailRequest;
import com.workly.dto.VerifyEmailResponse;
// attendance DTOs removed
import com.workly.entity.Employee;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.repo.TaskRepository;
import com.workly.service.EmployeeService;
import com.workly.service.FileService;
import com.workly.service.EmailVerificationService;
// attendance service removed
import com.workly.service.TaskService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final TaskService taskService;
    
    @Autowired
    private EmployeeService employeeService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private TaskAssignmentRepository assignmentRepo;
    
    @Autowired
    private TaskRepository taskRepo;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            Employee employee = employeeService.createEmployee(request);
            return ResponseEntity.ok(employee);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to create user: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailRequest request) {
        try {
            VerifyEmailResponse response = emailVerificationService.sendVerificationEmail(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Verify email failed for {}", request.getEmail(), e);
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to send verification email: " + e.getMessage()));
        }
    }

    @PostMapping("/create-task")
    public ResponseEntity<Task> createTask(@RequestBody CreateTaskRequest request, Authentication auth) {
        String adminEmpId = auth.getName();
        Task task = taskService.createTask(request, adminEmpId);
        return ResponseEntity.ok(task);
    }

    @PostMapping(value = "/create-task-with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createTaskWithFile(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("taskType") String taskType,
            @RequestParam("empId") String empId,
            @RequestParam("dueDate") String dueDate,
            @RequestParam("requiresSubmission") Boolean requiresSubmission,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) {
        try {
            CreateTaskWithFileRequest request = new CreateTaskWithFileRequest();
            request.setTitle(title);
            request.setDescription(description);
            request.setTaskType(com.workly.entity.TaskType.valueOf(taskType));
            request.setDocument(file);

            String adminEmpId = auth.getName();
            Task task = taskService.createTaskWithFile(request, adminEmpId);
            
            // Automatically assign the task to the specified employee
            AssignTaskRequest assignRequest = new AssignTaskRequest();
            assignRequest.setTaskId(task.getId());
            assignRequest.setEmpId(empId);
            assignRequest.setDueDate(LocalDate.parse(dueDate)); // Parse the string date to LocalDate
            assignRequest.setRequiresSubmission(requiresSubmission);
            
            TaskAssignment assignment = taskService.assignTask(assignRequest, adminEmpId);
            return ResponseEntity.ok(assignment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating and assigning task: " + e.getMessage());
        }
    }

    @PostMapping("/assign-task")
    public ResponseEntity<TaskAssignment> assignTask(@RequestBody AssignTaskRequest request, Authentication auth) {
        String adminEmpId = auth.getName();
        TaskAssignment assignment = taskService.assignTask(request, adminEmpId);
        return ResponseEntity.ok(assignment);
    }

    @PostMapping("/reassign-task")
    public ResponseEntity<TaskAssignment> reassignTask(@RequestBody ReassignTaskRequest request, Authentication auth) {
        String adminEmpId = auth.getName();
        TaskAssignment assignment = taskService.reassignTask(request, adminEmpId);
        return ResponseEntity.ok(assignment);
    }

    @GetMapping("/tasks/{empId}")
    public ResponseEntity<List<TaskAssignment>> getTasksByEmpId(@PathVariable String empId) {
        List<TaskAssignment> tasks = taskService.getTasksByEmpId(empId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/all-assignments")
    public ResponseEntity<List<TaskAssignment>> getAllTaskAssignments() {
        List<TaskAssignment> assignments = assignmentRepo.findAll();
        return ResponseEntity.ok(assignments);
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getAllTasks() {
        List<Task> tasks = taskRepo.findAll();
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/employees")
    public ResponseEntity<List<Employee>> getAllEmployees() {
        List<Employee> employees = employeeService.getAllEmployees();
        return ResponseEntity.ok(employees);
    }

    // Attendance endpoints removed — feature disabled/cleaned up

    @GetMapping("/download-document/{type}/{id}")
    public ResponseEntity<ByteArrayResource> downloadDocument(
            @PathVariable String type, 
            @PathVariable Long id) {
        try {
            String documentPath = null;
            String filename = null;
            
            switch (type.toLowerCase()) {
                case "task":
                    // Download original task document
                    Task task = taskRepo.findById(id).orElseThrow();
                    documentPath = task.getDocumentPath();
                    break;
                    
                case "assignment":
                    // Download assignment document (with empId naming)
                    TaskAssignment assignment = assignmentRepo.findById(id).orElseThrow();
                    documentPath = assignment.getAssignmentDocPath() != null ? 
                        assignment.getAssignmentDocPath() : assignment.getTask().getDocumentPath();
                    break;
                    
                case "submission":
                    // Download submitted document
                    TaskAssignment submissionAssignment = assignmentRepo.findById(id).orElseThrow();
                    documentPath = submissionAssignment.getSubmissionDocPath();
                    break;
                    
                default:
                    return ResponseEntity.badRequest().build();
            }
            
            if (documentPath == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = fileService.downloadFile(documentPath);
            filename = fileService.getFileName(documentPath);
            
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
