package com.workly.repo;

import com.workly.entity.CommunicationAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationAttachmentRepository extends JpaRepository<CommunicationAttachment, Long> {
    Optional<CommunicationAttachment> findByIdAndMessageId(Long id, Long messageId);
}
