package com.workly.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class FileService {

    private final String storageProvider;
    private final Path localUploadDir;
    private final String s3Bucket;
    private final String s3KeyPrefix;
    private final S3Client s3Client;

    public FileService(
            @Value("${app.storage.provider:local}") String storageProvider,
            @Value("${file.upload.dir:uploads/}") String localUploadDir,
            @Value("${aws.s3.bucket:}") String s3Bucket,
            @Value("${aws.s3.key-prefix:workly}") String s3KeyPrefix,
            @Value("${aws.region:${AWS_REGION:ap-south-2}}") String awsRegion) {
        this.storageProvider = normalize(storageProvider, "local");
        this.localUploadDir = Paths.get(localUploadDir).normalize();
        this.s3Bucket = s3Bucket == null ? "" : s3Bucket.trim();
        this.s3KeyPrefix = normalizePrefix(s3KeyPrefix);
        this.s3Client = "s3".equals(this.storageProvider)
                ? S3Client.builder().region(Region.of(awsRegion)).build()
                : null;
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String extension = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        return storeFile(file.getBytes(), folder, fileName);
    }

    public String uploadFileWithCustomName(MultipartFile file, String folder, String customFileName) throws IOException {
        String fileName = customFileName + getExtension(file.getOriginalFilename());
        return storeFile(file.getBytes(), folder, fileName);
    }

    public String copyFileWithCustomName(String sourcePath, String folder, String customFileName) throws IOException {
        String fileName = customFileName + getExtension(sourcePath);
        if (isS3Path(sourcePath)) {
            String destinationKey = buildS3Key(folder, fileName);
            ensureS3Configured();
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(s3Bucket)
                    .sourceKey(extractS3Key(sourcePath))
                    .destinationBucket(s3Bucket)
                    .destinationKey(destinationKey)
                    .build());
            return toS3Uri(destinationKey);
        }

        Path source = Paths.get(sourcePath);
        Path destinationDirectory = localUploadDir.resolve(folder).normalize();
        Files.createDirectories(destinationDirectory);
        Path destination = destinationDirectory.resolve(fileName);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toString().replace('\\', '/');
    }

    public byte[] downloadFile(String filePath) throws IOException {
        if (isS3Path(filePath)) {
            ensureS3Configured();
            try {
                ResponseBytes<?> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(extractS3Key(filePath))
                        .build());
                return response.asByteArray();
            } catch (NoSuchKeyException ex) {
                throw new IOException("File not found in S3: " + filePath, ex);
            }
        }

        return Files.readAllBytes(Paths.get(filePath));
    }

    public boolean deleteFile(String filePath) {
        try {
            if (isS3Path(filePath)) {
                ensureS3Configured();
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(extractS3Key(filePath))
                        .build());
                return true;
            }
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (Exception e) {
            return false;
        }
    }

    public String getFileName(String filePath) {
        if (isS3Path(filePath)) {
            String key = extractS3Key(filePath);
            return key.substring(key.lastIndexOf('/') + 1);
        }
        return Paths.get(filePath).getFileName().toString();
    }

    private String storeFile(byte[] bytes, String folder, String fileName) throws IOException {
        if ("s3".equals(storageProvider)) {
            ensureS3Configured();
            String key = buildS3Key(folder, fileName);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(key)
                    .build(), RequestBody.fromBytes(bytes));
            return toS3Uri(key);
        }

        Path directory = localUploadDir.resolve(folder).normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName);
        Files.write(target, bytes);
        return target.toString().replace('\\', '/');
    }

    private void ensureS3Configured() {
        if (s3Client == null || s3Bucket.isBlank()) {
            throw new IllegalStateException("S3 storage is enabled but AWS_S3_BUCKET is not configured.");
        }
    }

    private boolean isS3Path(String path) {
        return path != null && path.startsWith("s3://");
    }

    private String extractS3Key(String s3Path) {
        URI uri = URI.create(s3Path);
        return uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
    }

    private String toS3Uri(String key) {
        return "s3://" + s3Bucket + "/" + key;
    }

    private String buildS3Key(String folder, String fileName) {
        String folderPart = folder == null ? "" : folder.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        String prefix = s3KeyPrefix.isBlank() ? "" : s3KeyPrefix + "/";
        return prefix + folderPart + "/" + fileName;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase();
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
