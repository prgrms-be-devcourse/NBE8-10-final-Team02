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
     * PGroonga 전문 검색(FTS) — extracted_text + original_file_name 통합 검색.
     *
     * <p>{@code &@~} 연산자: PGroonga의 한국어/영어 형태소 분석 + Fuzzy search.
     * 두 컬럼을 한 쿼리에서 OR로 검색하고, 컬럼별 score의 최댓값으로 정렬한다.</p>
     *
     * <ul>
     *   <li>extracted_text 매칭은 {@code extract_status = 'success'}인 문서로 제한.</li>
     *   <li>original_file_name 매칭은 추출 상태와 무관(PENDING 문서도 파일명으로 검색 가능).</li>
     *   <li>두 컬럼 모두 매칭되면 더 큰 score 기준으로 한 행만 반환.</li>
     * </ul>
     *
     * @param userId 검색 대상 사용자 ID (다른 사용자 문서 접근 차단)
     * @param query  검색 키워드
     * @return 검색 결과 문서 목록 (최대 20개, 관련도 내림차순)
     */
    @Query(value = """
        SELECT * FROM documents
        WHERE user_id = :userId
          AND (
            (extract_status = 'success' AND extracted_text &@~ :query)
            OR original_file_name &@~ :query
          )
        ORDER BY pgroonga_score(tableoid, ctid) DESC
        LIMIT 20
        """, nativeQuery = true)
    List<Document> search(@Param("userId") Long userId, @Param("query") String query);
}
