package com.workly.service;

import com.workly.dto.FeedbackDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FeedbackService {
    private final Path feedbackFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public FeedbackService() {
        this.feedbackFile = Paths.get("uploads", "feedbacks.json");
        try {
            Path uploadsDir = feedbackFile.getParent();
            if (uploadsDir != null && !Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }
            if (!Files.exists(feedbackFile)) {
                Files.write(feedbackFile, "[]".getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize feedback storage", e);
        }
    }

    public synchronized List<FeedbackDto> saveFeedback(FeedbackDto feedback) {
        try {
            List<FeedbackDto> existing = readAll();
            // Prepend new feedback
            existing.add(0, feedback);
            mapper.writerWithDefaultPrettyPrinter().writeValue(feedbackFile.toFile(), existing);
            return existing;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save feedback", e);
        }
    }

    public synchronized List<FeedbackDto> readAll() {
        try {
            if (!Files.exists(feedbackFile)) return new ArrayList<>();
            byte[] bytes = Files.readAllBytes(feedbackFile);
            if (bytes.length == 0) return new ArrayList<>();
            List<FeedbackDto> list = mapper.readValue(bytes, new TypeReference<List<FeedbackDto>>() {});
            if (list == null) return new ArrayList<>();
            return list;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read feedback file", e);
        }
    }
}
