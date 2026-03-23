package com.back.backend.document.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일을 물리적으로 저장하는 storage 추상화 인터페이스.
 *
 * <p>현재 구현체는 로컬 디스크 기반의 {@link LocalDocumentStorageService}이며,
 * 추후 S3 등 외부 스토리지로 교체할 때는 이 인터페이스의 새 구현체를 추가.
 * {@link com.back.backend.document.service.DocumentService}는 이 인터페이스에만 의존하므로
 * 구현체 교체 시 service 코드를 수정할 필요가 없다.</p>
 */
public interface DocumentStorageService {

    /**
     * 파일을 저장하고 storagePath를 반환한다.
     * 저장 실패 시 ServiceException(DOCUMENT_UPLOAD_FAILED)을 던진다.
     *
     * @param file 저장할 파일
     * @return 저장된 파일의 경로 문자열 (DB의 {@code storage_path} 컬럼에 저장됨)
     */
    String store(MultipartFile file);
}
