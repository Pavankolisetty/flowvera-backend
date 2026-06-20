package com.workly.repo;

import com.workly.entity.TaskAssignment;
import com.workly.entity.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByEmployeeEmpId(String empId);
    List<TaskAssignment> findByEmployeeDepartmentIgnoreCase(String department);
    Optional<TaskAssignment> findByIdAndEmployeeEmpId(Long id, String empId);
    List<TaskAssignment> findByEmployeeEmpIdAndStatusNot(String empId, TaskStatus status);
    List<TaskAssignment> findByAssignedByOrderByAssignedAtDesc(String assignedBy);
    List<TaskAssignment> findByTaskId(Long taskId);
    @EntityGraph(attributePaths = {"task", "employee", "progressHistory"})
    List<TaskAssignment> findAllByOrderByAssignedAtDesc();
    long countByEmployeeEmpId(String empId);
    long countByEmployeeEmpIdAndStatus(String empId, TaskStatus status);
    
    // Check if task is already assigned to employee
    boolean existsByTaskIdAndEmployeeEmpId(Long taskId, String empId);
}

