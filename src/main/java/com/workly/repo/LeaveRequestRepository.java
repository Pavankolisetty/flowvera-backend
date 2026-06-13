package com.workly.repo;

import com.workly.entity.LeaveRequest;
import com.workly.entity.LeaveRequestStatus;
import com.workly.entity.LeaveRequestType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeEmpIdOrderByCreatedAtDesc(String empId);

    List<LeaveRequest> findByEmployeeEmpIdAndStatusInOrderByCreatedAtDesc(
        String empId,
        Collection<LeaveRequestStatus> statuses
    );

    List<LeaveRequest> findByEmployeeEmpIdAndRequestTypeAndStatusAndStartDateGreaterThanEqual(
        String empId,
        LeaveRequestType requestType,
        LeaveRequestStatus status,
        LocalDate startDate
    );

    @Query("""
        select lr from LeaveRequest lr
        where lr.employee.empId = :empId
          and lr.status in :statuses
          and lr.startDate <= :endDate
          and lr.endDate >= :startDate
    """)
    List<LeaveRequest> findOverlappingRequests(
        @Param("empId") String empId,
        @Param("statuses") Collection<LeaveRequestStatus> statuses,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
