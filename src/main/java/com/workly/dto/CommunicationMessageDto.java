package com.workly.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CommunicationMessageDto {
    private Long id;
    private String senderEmpId;
    private String senderName;
    private String senderDepartment;
    private String audience;
    private String targetEmpId;
    private String targetName;
    private String targetDepartment;
    private String body;
    private LocalDateTime createdAt;
    private boolean sentByMe;
    private List<CommunicationAttachmentDto> attachments = new ArrayList<>();
}
