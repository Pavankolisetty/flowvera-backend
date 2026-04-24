package com.workly.controller;

import com.workly.dto.AdminAttendanceEmployeeDto;
import com.workly.dto.ApproveUserRequest;
import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.EmployeeAttendanceOverviewDto;
import com.workly.dto.ErrorResponse;
import com.workly.dto.ReassignTaskRequest;
import com.workly.dto.ReviewSubmissionRequest;
import com.workly.dto.TaskActionResponse;
import com.workly.entity.Employee;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.repo.TaskRepository;
import com.workly.service.AttendanceService;
import com.workly.service.EmployeeService;
import com.workly.service.FileService;
import com.workly.service.TaskService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

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
    private AttendanceService attendanceService;

    @GetMapping("/pending-users")
    public ResponseEntity<List<Employee>> getPendingUsers() {
        return ResponseEntity.ok(employeeService.getPendingEmployees());
    }

    @PostMapping("/approve-user/{id}")
    public ResponseEntity<?> approveUser(@PathVariable("id") String pendingUserId, @RequestBody ApproveUserRequest request) {
        try {
            Employee employee = employeeService.approvePendingUser(pendingUserId, request);
            return ResponseEntity.ok(employee);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to approve user: " + e.getMessage()));
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

            AssignTaskRequest assignRequest = new AssignTaskRequest();
            assignRequest.setTaskId(task.getId());
            assignRequest.setEmpId(empId);
            assignRequest.setDueDate(LocalDate.parse(dueDate));
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
        return ResponseEntity.ok(taskService.getTasksByEmpId(empId));
    }

    @GetMapping("/all-assignments")
    public ResponseEntity<List<TaskAssignment>> getAllTaskAssignments() {
        return ResponseEntity.ok(taskService.getAllAssignments());
    }

    @PostMapping("/submission/request-changes")
    public ResponseEntity<?> requestSubmissionChanges(@RequestBody ReviewSubmissionRequest request, Authentication auth) {
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

    @PostMapping("/submission/accept/{assignmentId}")
    public ResponseEntity<?> acceptSubmission(@PathVariable Long assignmentId, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.acceptSubmission(assignmentId, auth.getName());
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Submission accepted successfully. The task is now completed."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/notifications/read")
    public ResponseEntity<List<TaskAssignment>> markAdminNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(taskService.markReviewerNotificationsRead(auth.getName()));
    }

    @PostMapping("/due-date-extension/approve/{assignmentId}")
    public ResponseEntity<?> approveDueDateExtension(@PathVariable Long assignmentId, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.approveDueDateExtension(assignmentId, auth.getName());
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Due date extension approved successfully."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskRepo.findAll());
    }

    @GetMapping("/employees")
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @GetMapping("/attendance/today")
    public ResponseEntity<List<AdminAttendanceEmployeeDto>> getTodayAttendance() {
        return ResponseEntity.ok(attendanceService.getTodayAttendanceForAdmin());
    }

    @GetMapping("/attendance/employee/{empId}")
    public ResponseEntity<EmployeeAttendanceOverviewDto> getEmployeeAttendance(
            @PathVariable String empId,
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestParam(value = "month", required = false) YearMonth month) {
        return ResponseEntity.ok(attendanceService.getEmployeeHistory(empId, days, null, month));
    }

    @GetMapping("/download-document/{type}/{id}")
    public ResponseEntity<ByteArrayResource> downloadDocument(
            @PathVariable String type,
            @PathVariable Long id) {
        try {
            String documentPath;

            switch (type.toLowerCase()) {
                case "task":
                    Task task = taskRepo.findById(id).orElseThrow();
                    documentPath = task.getDocumentPath();
                    break;
                case "assignment":
                    TaskAssignment assignment = assignmentRepo.findById(id).orElseThrow();
                    documentPath = assignment.getAssignmentDocPath() != null
                        ? assignment.getAssignmentDocPath()
                        : assignment.getTask().getDocumentPath();
                    break;
                case "submission":
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
