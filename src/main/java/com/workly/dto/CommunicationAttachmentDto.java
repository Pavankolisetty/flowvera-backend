package com.workly.dto;

import lombok.Data;

@Data
public class CommunicationAttachmentDto {
    private Long id;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
}
