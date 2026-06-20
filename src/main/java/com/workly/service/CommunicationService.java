package com.workly.service;

import com.workly.dto.CommunicationMessageDto;
import com.workly.dto.CommunicationRecipientOptionsDto;
import com.workly.dto.CommunicationSummaryDto;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CommunicationService {
    CommunicationRecipientOptionsDto getRecipientOptions(String empId);
    CommunicationSummaryDto getSummary(String empId);
    List<CommunicationMessageDto> getMessages(String empId);
    CommunicationMessageDto sendMessage(String empId, String audience, String targetEmpId, String targetDepartment, String body, MultipartFile file) throws IOException;
    byte[] downloadAttachment(String empId, Long attachmentId) throws IOException;
    String getAttachmentFileName(Long attachmentId);
    String getAttachmentContentType(Long attachmentId);
    void markSeen(String empId);
}
