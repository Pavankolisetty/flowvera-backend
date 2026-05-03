package com.workly.controller;

import com.workly.dto.AssignTaskRequest;
import com.workly.dto.AttendanceActionRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.DueDateExtensionRequest;
import com.workly.dto.EmployeeAttendanceOverviewDto;
import com.workly.dto.EmployeeProfileResponse;
import com.workly.dto.ErrorResponse;
import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestDto;
import com.workly.dto.ReviewSubmissionRequest;
import com.workly.dto.TaskActionResponse;
import com.workly.dto.UpdatePasswordRequest;
import com.workly.dto.UpdateProgressRequest;
import com.workly.entity.Employee;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskType;
import com.workly.repo.TaskAssignmentRepository;
import com.workly.service.AttendanceService;
import com.workly.service.EmployeeService;
import com.workly.service.FileService;
import com.workly.service.LeaveRequestService;
import com.workly.service.TaskService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final TaskService taskService;
    private final AttendanceService attendanceService;
    private final LeaveRequestService leaveRequestService;

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
        return ResponseEntity.ok(employeeService.viewMyTasks(auth.getName()));
    }

    @GetMapping("/my-tasks/active")
    public ResponseEntity<List<TaskAssignment>> myActiveTasks(Authentication auth) {
        return ResponseEntity.ok(employeeService.viewMyActiveTasks(auth.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<EmployeeProfileResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(employeeService.getProfile(auth.getName()));
    }

    @GetMapping("/attendance")
    public ResponseEntity<EmployeeAttendanceOverviewDto> getAttendanceOverview(
            Authentication auth,
            @RequestParam(value = "sessionKey", required = false) String sessionKey,
            @RequestParam(value = "month", required = false) String month) {
        YearMonth targetMonth = month == null || month.isBlank() ? null : YearMonth.parse(month);
        return ResponseEntity.ok(attendanceService.getEmployeeOverview(auth.getName(), sessionKey, targetMonth));
    }

    @PostMapping("/attendance/clock-in")
    public ResponseEntity<EmployeeAttendanceOverviewDto> clockIn(
            @RequestBody AttendanceActionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.clockIn(auth.getName(), request.getSessionKey()));
    }

    @PostMapping("/attendance/heartbeat")
    public ResponseEntity<EmployeeAttendanceOverviewDto> heartbeat(
            @RequestBody AttendanceActionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.heartbeat(auth.getName(), request.getSessionKey()));
    }

    @PostMapping("/attendance/clock-out")
    public ResponseEntity<EmployeeAttendanceOverviewDto> clockOut(
            @RequestBody AttendanceActionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.clockOut(auth.getName(), request.getSessionKey()));
    }

    @GetMapping("/leave-requests")
    public ResponseEntity<List<LeaveRequestDto>> getMyLeaveRequests(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.getMyRequests(auth.getName()));
    }

    @PostMapping("/leave-requests")
    public ResponseEntity<?> requestLeave(@RequestBody LeaveRequestCreateRequest request, Authentication auth) {
        try {
            return ResponseEntity.ok(leaveRequestService.createRequest(auth.getName(), request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/leave-requests/notifications/read")
    public ResponseEntity<List<LeaveRequestDto>> markLeaveNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.markEmployeeNotificationsRead(auth.getName()));
    }

    @GetMapping("/managed-leave-requests")
    public ResponseEntity<List<LeaveRequestDto>> getManagedLeaveRequests(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.getManagedRequests(auth.getName()));
    }

    @PutMapping("/managed-leave-requests/notifications/read")
    public ResponseEntity<List<LeaveRequestDto>> markManagedLeaveNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.markManagerNotificationsRead(auth.getName()));
    }

    @PostMapping("/managed-leave-requests/approve/{requestId}")
    public ResponseEntity<?> approveManagedLeaveRequest(@PathVariable Long requestId, Authentication auth) {
        try {
            return ResponseEntity.ok(leaveRequestService.approveRequest(requestId, auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

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
        Employee requester = employeeService.findByEmpId(empId);
        if (requester == null) {
            return ResponseEntity.ok(List.of());
        }
        if (requester.getRole() != com.workly.entity.Role.ADMIN && !Boolean.TRUE.equals(requester.getCanAssignTask())) {
            return ResponseEntity.ok(List.of());
        }
        List<Employee> employees = employeeService.getAllEmployees().stream()
            .filter(employee -> employee.getRole() != com.workly.entity.Role.ADMIN)
            .filter(employee -> Boolean.TRUE.equals(employee.getIsApproved()))
            .filter(employee -> !employee.getEmpId().equals(empId))
            .filter(employee -> requester.getRole() == com.workly.entity.Role.ADMIN
                || empId.equals(employee.getReportingManagerEmpId()))
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

    @PostMapping("/due-date-extension/request")
    public ResponseEntity<?> requestDueDateExtension(@RequestBody DueDateExtensionRequest request, Authentication auth) {
        try {
            TaskAssignment assignment = taskService.requestDueDateExtension(request, auth.getName());
            return ResponseEntity.ok(new TaskActionResponse(
                assignment,
                "Due date extension request sent successfully."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/delegated/due-date-extension/approve/{assignmentId}")
    public ResponseEntity<?> approveDelegatedDueDateExtension(@PathVariable Long assignmentId, Authentication auth) {
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

            String documentPath = assignment.getAssignmentDocPath() != null
                ? assignment.getAssignmentDocPath()
                : assignment.getTask().getDocumentPath();

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
