package com.workly.repo;

import com.workly.entity.CommunicationAudience;
import com.workly.entity.CommunicationMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, Long> {

    @Query("""
        select distinct m from CommunicationMessage m
        left join fetch m.attachments
        where m.sender.empId = :empId
           or m.id in :messageIds
        order by m.createdAt desc
        """)
    List<CommunicationMessage> findVisibleMessages(
        @Param("empId") String empId,
        @Param("messageIds") Collection<Long> messageIds,
        Pageable pageable);

    @Query("""
        select distinct m from CommunicationMessage m
        left join fetch m.attachments
        where m.sender.empId = :empId
        order by m.createdAt desc
        """)
    List<CommunicationMessage> findSentMessages(@Param("empId") String empId, Pageable pageable);

    List<CommunicationMessage> findTop50ByAudienceOrderByCreatedAtDesc(CommunicationAudience audience);
}
