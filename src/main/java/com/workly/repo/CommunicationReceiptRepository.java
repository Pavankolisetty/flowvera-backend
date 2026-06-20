package com.workly.repo;

import com.workly.entity.CommunicationReceipt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationReceiptRepository extends JpaRepository<CommunicationReceipt, Long> {
    List<CommunicationReceipt> findByRecipientEmpIdOrderByMessageCreatedAtDesc(String empId);
    boolean existsByRecipientEmpIdAndUnreadTrue(String empId);

    @Query("select r.message.id from CommunicationReceipt r where r.recipient.empId = :empId")
    List<Long> findMessageIdsForRecipient(@Param("empId") String empId);

    @Modifying
    @Query("update CommunicationReceipt r set r.unread = false, r.seenAt = CURRENT_TIMESTAMP where r.recipient.empId = :empId and r.unread = true")
    int markAllSeen(@Param("empId") String empId);
}
