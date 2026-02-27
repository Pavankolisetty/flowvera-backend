package com.workly.controller;

import com.workly.dto.FeedbackDto;
import com.workly.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ResponseEntity<List<FeedbackDto>> getAll() {
        return ResponseEntity.ok(feedbackService.readAll());
    }

    @PostMapping
    public ResponseEntity<FeedbackDto> create(@RequestBody FeedbackDto feedback) {
        if (feedback.getId() == 0) {
            feedback.setId(System.currentTimeMillis());
        }
        if (feedback.getTimestamp() == null || feedback.getTimestamp().isBlank()) {
            feedback.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (feedback.getCount() == 0) feedback.setCount(1);
        feedbackService.saveFeedback(feedback);
        return ResponseEntity.ok(feedback);
    }
}
