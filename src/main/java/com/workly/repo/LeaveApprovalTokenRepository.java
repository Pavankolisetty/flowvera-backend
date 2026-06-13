package com.workly.repo;

import com.workly.entity.LeaveApprovalToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveApprovalTokenRepository extends JpaRepository<LeaveApprovalToken, Long> {
    Optional<LeaveApprovalToken> findByToken(String token);
    List<LeaveApprovalToken> findByLeaveRequestId(Long leaveRequestId);
}
