package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

// application_source_documents 테이블 접근 인터페이스
// 문서 삭제 시 지원 단위(application)에서 참조 중인지 확인하는 데 사용한다
public interface ApplicationSourceDocumentRepository extends JpaRepository<ApplicationSourceDocument, Long> {

    // 해당 문서가 하나 이상의 지원 단위에 연결돼 있으면 true → 삭제 불가(409)
    boolean existsByDocumentId(Long documentId);
}
