package com.back.backend.document.storage;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorageService.class);

    private final Path uploadDir;

    public LocalDocumentStorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
            Path destination = uploadDir.resolve(filename);
            file.transferTo(destination);
            return uploadDir.getFileName().toString() + "/" + filename;
        } catch (IOException e) {
            log.error("Document storage failed: {}", e.getMessage(), e);
            throw new ServiceException(
                    ErrorCode.DOCUMENT_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "파일 저장에 실패했습니다.",
                    true
            );
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}