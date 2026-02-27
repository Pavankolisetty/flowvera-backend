package com.workly.dto;

import com.workly.entity.TaskType;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateTaskWithFileRequest {
    private String title;
    private String description;
    private TaskType taskType;
    private MultipartFile document; // Optional document for DOC_TEXT tasks
}