package com.back.backend.domain.document.storage;

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

/**
 * 로컬 디스크에 파일을 저장하는 {@link DocumentStorageService} 구현체.
 *
 * <p>저장 경로는 {@code app.storage.upload-dir} 프로퍼티로 설정한다.
 * 파일명은 {@code UUID_원본파일명} 형식으로 생성해 충돌을 방지하며,
 * 원본 파일명의 특수문자는 {@code _}로 치환해 path traversal 공격을 차단한다.</p>
 *
 * <p>추후 S3 등으로 교체할 경우 이 클래스를 대체하는 새 구현체를 만들고
 * {@link DocumentStorageService} 빈으로 등록하면 된다.</p>
 */
@Component
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorageService.class);

    private final Path uploadDir;

    public LocalDocumentStorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 파일을 로컬 디스크에 저장하고 상대 경로를 반환한다.
     *
     * <p>저장 파일명 형식: {@code UUID_sanitized원본파일명}
     * 반환 경로 형식: {@code 업로드디렉토리명/파일명} (DB storage_path 컬럼에 저장됨)</p>
     *
     * @param file 저장할 파일
     * @return 저장 경로 문자열
     * @throws ServiceException 파일 I/O 실패 시 DOCUMENT_UPLOAD_FAILED 코드로 던진다.
     */
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

    /**
     * storagePath에 해당하는 물리 파일을 삭제한다.
     *
     * <p>storagePath에서 파일명만 추출해 uploadDir 기준으로 resolve한다.
     * 파일이 존재하지 않으면 무시하고, 삭제 실패 시 WARN 로그만 남긴다.
     * DB 삭제가 이미 완료된 시점에서 호출되므로 예외를 던지지 않는다.</p>
     */
    @Override
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            // storagePath 형식: "uploads/uuid_filename.pdf" → 파일명만 추출
            String filename = Paths.get(storagePath).getFileName().toString();
            Path filePath = uploadDir.resolve(filename);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Physical file deleted: {}", filename);
            }
        } catch (IOException e) {
            log.warn("Failed to delete physical file for storagePath={}: {}", storagePath, e.getMessage());
        }
    }

    /**
     * 파일명에서 영문자, 숫자, ., _, - 외의 문자를 _로 치환한다.
     * null이거나 빈 값이면 "unnamed"를 반환한다.
     */
    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
