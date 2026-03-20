package com.back.backend.document.storage;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {

    /**
     * 파일을 저장하고 storagePath를 반환한다.
     * 저장 실패 시 ServiceException(DOCUMENT_UPLOAD_FAILED)을 던진다.
     */
    String store(MultipartFile file);
}