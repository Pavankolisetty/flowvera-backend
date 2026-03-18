package com.workly.service;

import com.workly.dto.AssignTaskRequest;
import com.workly.dto.CreateTaskRequest;
import com.workly.dto.CreateTaskWithFileRequest;
import com.workly.dto.ReassignTaskRequest;
import com.workly.dto.ReviewSubmissionRequest;
import com.workly.entity.Task;
import com.workly.entity.TaskAssignment;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface TaskService {
    Task createTask(CreateTaskRequest request, String createdBy);
    Task createTaskWithFile(CreateTaskWithFileRequest request, String createdBy) throws Exception;
    TaskAssignment assignTask(AssignTaskRequest request, String assignedBy);
    TaskAssignment reassignTask(ReassignTaskRequest request, String adminEmpId);
    List<TaskAssignment> getTasksByEmpId(String empId);
    TaskAssignment submitDocument(Long taskAssignmentId, MultipartFile file, String empId) throws Exception;
    TaskAssignment requestSubmissionChanges(ReviewSubmissionRequest request, String adminEmpId);
    TaskAssignment acceptSubmission(Long taskAssignmentId, String adminEmpId);
    List<TaskAssignment> markEmployeeNotificationsRead(String empId);
    List<TaskAssignment> markAdminNotificationsRead();
}

