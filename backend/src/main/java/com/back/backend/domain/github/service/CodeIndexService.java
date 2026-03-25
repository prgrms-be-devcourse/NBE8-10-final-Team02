package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;

import java.util.List;
import java.util.Set;

/**
 * Code Index Store 서비스 인터페이스 (설계 §8).
 *
 * 역할:
 *   - 분석 결과(AnalysisNode + PageRank)를 code_index 테이블에 저장
 *   - Gemini Function Calling tool 요청에 응답: 소스 원문 반환
 *   - Token Budget 적용을 위한 PageRank 필터링
 *
 * 소스 원문은 OCI 파일시스템의 clone에서 직접 읽는다 (DB에 저장하지 않음).
 */
public interface CodeIndexService {

    /**
     * 정적 분석 결과를 code_index 테이블에 저장한다.
     * 기존 데이터가 있으면 전체 교체(delete + insert) 한다.
     *
     * @param repo         대상 github_repositories 엔티티
     * @param userId       소유자 userId (clone 경로 구성용)
     * @param nodes        분석된 코드 노드 목록
     * @param authoredFiles 본인 기여 파일 상대 경로 집합 (authored_by_me 플래그용)
     * @param pagerankMap  CallGraphService가 계산한 pagerank 맵
     */
    void buildIndex(GithubRepository repo, Long userId,
                    List<AnalysisNode> nodes, Set<String> authoredFiles,
                    java.util.Map<String, Double> pagerankMap);

    /**
     * Token Budget 적용 후 포함할 노드 목록을 반환한다.
     * pagerank >= minPageRank인 노드만 반환 (PageRank 임계값 조정 §7).
     */
    List<CodeIndex> getTopByPageRank(GithubRepository repo, double minPageRank);

    /** 본인 기여 파일의 코드 노드만 반환한다 (외부 repo 분석 집중 §6). */
    List<CodeIndex> getAuthoredEntries(GithubRepository repo);

    /**
     * FQN으로 클래스 소스 원문을 반환한다 (Gemini Function Calling: get_class_source).
     * 정확한 FQN 매칭 실패 시 부분 매칭으로 후보를 찾는다.
     *
     * 주의: clone 경로에서 직접 파일을 읽으므로 분석 파이프라인 실행 중에만 유효하다.
     *       파이프라인 완료 후 clone이 삭제되면 빈 문자열을 반환한다.
     *       파이프라인 외부(controller 등)에서 호출하지 말 것.
     *
     * @return 소스 원문 문자열 (없으면 빈 문자열)
     */
    String getClassSource(GithubRepository repo, Long userId, String fqn);

    /**
     * 파일 경로로 소스 원문을 반환한다 (Gemini Function Calling: get_file_source).
     *
     * 주의: clone 경로에서 직접 파일을 읽으므로 분석 파이프라인 실행 중에만 유효하다.
     *       파이프라인 완료 후 clone이 삭제되면 빈 문자열을 반환한다.
     *       파이프라인 외부(controller 등)에서 호출하지 말 것.
     *
     * @return 소스 원문 문자열 (없으면 빈 문자열)
     */
    String getFileSource(GithubRepository repo, Long userId, String filePath);

    /**
     * 메서드 단위 소스를 반환한다 (Gemini Function Calling: get_method_source).
     * 클래스 소스에서 메서드 위치(locStart~locEnd)만 추출한다.
     *
     * 주의: clone 경로에서 직접 파일을 읽으므로 분석 파이프라인 실행 중에만 유효하다.
     *       파이프라인 완료 후 clone이 삭제되면 빈 문자열을 반환한다.
     *       파이프라인 외부(controller 등)에서 호출하지 말 것.
     *
     * @return 메서드 소스 문자열 (없으면 빈 문자열)
     */
    String getMethodSource(GithubRepository repo, Long userId, String fqn, String methodName);

    /** repo의 code_index 전체 삭제 (repo 제거 시 §3.3). */
    void deleteByRepository(GithubRepository repo);
}
