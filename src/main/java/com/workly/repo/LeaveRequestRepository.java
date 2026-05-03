package com.workly.repo;

import com.workly.entity.LeaveRequest;
import com.workly.entity.LeaveRequestStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeEmpIdOrderByRequestDateDescRequestedAtDesc(String empId);
    List<LeaveRequest> findByManagerEmpIdOrderByRequestedAtDesc(String managerEmpId);
    List<LeaveRequest> findByEmployeeEmpIdAndRequestDateBetweenAndStatus(
        String empId,
        LocalDate start,
        LocalDate end,
        LeaveRequestStatus status
    );
    List<LeaveRequest> findByEmployeeEmpIdInAndRequestDateBetweenAndStatus(
        Collection<String> empIds,
        LocalDate start,
        LocalDate end,
        LeaveRequestStatus status
    );
    Optional<LeaveRequest> findFirstByEmployeeEmpIdAndRequestDateAndStatusIn(
        String empId,
        LocalDate requestDate,
        Collection<LeaveRequestStatus> statuses
    );
}
