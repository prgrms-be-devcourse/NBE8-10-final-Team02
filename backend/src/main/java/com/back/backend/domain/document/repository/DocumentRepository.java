package com.back.backend.domain.document.repository;


import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

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

     // 지원 단위 생성 시 선택한 문서 ID 목록을 한 번에 조회한다
    // userId 조건으로 다른 사용자 문서를 연결하는 것을 방지한다
    // 호출 전 ids가 비어 있으면 빈 리스트를 반환해야 한다 (빈 IN절 방지)
    List<Document> findAllByIdInAndUserId(Collection<Long> ids, Long userId);

    // 준비 현황 대시보드: 특정 추출 상태인 문서 수 집계
    int countByUserIdAndExtractStatus(Long userId, DocumentExtractStatus extractStatus);

    /**
     * PGroonga를 사용해 추출된 텍스트에서 전문 검색(FTS)을 수행한다.
     *
     * <p>{@code &@~} 연산자: PGroonga의 전문 검색 연산자로 한국어/영어 형태소 분석 및
     * Fuzzy search를 지원한다. PGroonga가 설치되지 않은 PostgreSQL에서는 오류가 발생한다.</p>
     *
     * <p>검색 결과는 PGroonga relevance score 내림차순으로 정렬되며,
     * {@code extract_status = 'success'}인 문서만 반환한다.</p>
     *
     * @param userId 검색 대상 사용자 ID (다른 사용자 문서 접근 차단)
     * @param query  검색 키워드 (한국어, 영어, Fuzzy 검색 지원)
     * @return 검색 결과 문서 목록 (최대 20개, 관련도 내림차순)
     */
    @Query(value = """
        SELECT * FROM documents
        WHERE user_id = :userId
          AND extract_status = 'success'
          AND extracted_text &@~ :query
        ORDER BY pgroonga_score(tableoid, ctid) DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Document> searchByText(@Param("userId") Long userId, @Param("query") String query);

    /**
     * PGroonga를 사용해 원본 파일명에서 전문 검색을 수행한다.
     *
     * <p>파일명 검색은 추출 상태와 무관하게 모든 문서에서 검색한다.
     * 업로드 직후 PENDING 상태인 문서도 파일명으로 찾을 수 있도록 한다.</p>
     *
     * @param userId 검색 대상 사용자 ID
     * @param query  검색 키워드
     * @return 검색 결과 문서 목록 (최대 20개, 관련도 내림차순)
     */
    @Query(value = """
        SELECT * FROM documents
        WHERE user_id = :userId
          AND original_file_name &@~ :query
        ORDER BY pgroonga_score(tableoid, ctid) DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Document> searchByFileName(@Param("userId") Long userId, @Param("query") String query);
}
