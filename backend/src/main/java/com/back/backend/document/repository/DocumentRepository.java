package com.back.backend.document.repository;


import com.back.backend.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Document entity에 대한 데이터 접근 인터페이스.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 특정 사용자의 문서 수를 반환한다.
     * 업로드 시 최대 갯수 제한 검증에 사용된다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 문서 수
     */
    int countByUserId(Long userId);

    // 목록 조회: 특정 사용자의 전체 문서를 반환한다
    List<Document> findAllByUserId(Long userId);

    // 상세/삭제 조회: 문서 ID와 사용자 ID를 함께 확인해 다른 사용자 문서 접근을 막는다
    Optional<Document> findByIdAndUserId(Long id, Long userId);
}
