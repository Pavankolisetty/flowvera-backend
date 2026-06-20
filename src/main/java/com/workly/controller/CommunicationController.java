package com.workly.controller;

import com.workly.dto.CommunicationMessageDto;
import com.workly.dto.CommunicationRecipientOptionsDto;
import com.workly.dto.CommunicationSummaryDto;
import com.workly.dto.ErrorResponse;
import com.workly.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/communication")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;

    @GetMapping("/options")
    public ResponseEntity<CommunicationRecipientOptionsDto> getOptions(Authentication auth) {
        return ResponseEntity.ok(communicationService.getRecipientOptions(auth.getName()));
    }

    @GetMapping("/summary")
    public ResponseEntity<CommunicationSummaryDto> getSummary(Authentication auth) {
        return ResponseEntity.ok(communicationService.getSummary(auth.getName()));
    }

    @PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMessage(
            @RequestParam("audience") String audience,
            @RequestParam(value = "targetEmpId", required = false) String targetEmpId,
            @RequestParam(value = "targetDepartment", required = false) String targetDepartment,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication auth) {
        try {
            CommunicationMessageDto message = communicationService.sendMessage(
                auth.getName(), audience, targetEmpId, targetDepartment, body, file);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/seen")
    public ResponseEntity<Void> markSeen(Authentication auth) {
        communicationService.markSeen(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<?> downloadAttachment(@PathVariable Long attachmentId, Authentication auth) {
        try {
            byte[] data = communicationService.downloadAttachment(auth.getName(), attachmentId);
            String filename = communicationService.getAttachmentFileName(attachmentId);
            String contentType = communicationService.getAttachmentContentType(attachmentId);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .body(new ByteArrayResource(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
}
