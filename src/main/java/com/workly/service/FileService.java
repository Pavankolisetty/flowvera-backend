package com.workly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileService {

    private final String uploadDir = "uploads/";

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        // Create directory if it doesn't exist
        File directory = new File(uploadDir + folder);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Generate unique filename
        String originalName = file.getOriginalFilename();
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID().toString() + extension;
        String filePath = uploadDir + folder + "/" + fileName;

        // Save file
        Path path = Paths.get(filePath);
        Files.write(path, file.getBytes());

        return filePath;
    }

    public String uploadFileWithCustomName(MultipartFile file, String folder, String customFileName) throws IOException {
        // Create directory if it doesn't exist
        File directory = new File(uploadDir + folder);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Get file extension
        String originalName = file.getOriginalFilename();
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : "";
        
        // Use custom filename with original extension
        String fileName = customFileName + extension;
        String filePath = uploadDir + folder + "/" + fileName;

        // Save file
        Path path = Paths.get(filePath);
        Files.write(path, file.getBytes());

        return filePath;
    }

    public String copyFileWithCustomName(String sourcePath, String folder, String customFileName) throws IOException {
        // Create directory if it doesn't exist
        File directory = new File(uploadDir + folder);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Get file extension from source
        String extension = sourcePath.substring(sourcePath.lastIndexOf("."));
        
        // Use custom filename with original extension
        String fileName = customFileName + extension;
        String targetPath = uploadDir + folder + "/" + fileName;

        // Copy file
        Path source = Paths.get(sourcePath);
        Path target = Paths.get(targetPath);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        return targetPath;
    }

    public byte[] downloadFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }

    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    public String getFileName(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }
}