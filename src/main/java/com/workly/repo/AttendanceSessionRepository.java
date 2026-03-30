package com.workly.repo;

import com.workly.entity.AttendanceSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findByEmployeeEmpIdAndSessionKeyAndActiveTrue(String empId, String sessionKey);
    List<AttendanceSession> findByEmployeeEmpIdAndActiveTrue(String empId);
    List<AttendanceSession> findByActiveTrueAndLastActivityAtBefore(LocalDateTime cutoff);
    List<AttendanceSession> findByEmployeeEmpIdAndClockInAtGreaterThanEqualOrderByClockInAtDesc(String empId, LocalDateTime start);
    List<AttendanceSession> findByClockInAtGreaterThanEqualOrderByClockInAtDesc(LocalDateTime start);
}
