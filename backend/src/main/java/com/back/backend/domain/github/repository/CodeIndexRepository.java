package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CodeIndexRepository extends JpaRepository<CodeIndex, Long> {

    // 특정 repo의 전체 인덱스
    List<CodeIndex> findByGithubRepository(GithubRepository repository);

    // pagerank 내림차순 (Token Budget 적용 시 상위 N개 선택)
    @Query("select c from CodeIndex c where c.githubRepository = :repo " +
           "and c.pagerank >= :minPageRank order by c.pagerank desc")
    List<CodeIndex> findByGithubRepositoryAndPagerankGreaterThanEqualOrderByPagerankDesc(
            @Param("repo") GithubRepository repository,
            @Param("minPageRank") double minPageRank);

    // 본인 기여 파일만 (외부 repo 요약 시 집중 분석 대상)
    List<CodeIndex> findByGithubRepositoryAndAuthoredByMeTrue(GithubRepository repository);

    // FQN 정확 매칭 (Gemini Function Calling: get_class_source)
    Optional<CodeIndex> findByGithubRepositoryAndFqn(GithubRepository repository, String fqn);

    // FQN 부분 매칭 (클래스명만 알 때 유사 검색)
    @Query("select c from CodeIndex c where c.githubRepository = :repo " +
           "and c.fqn like %:partialFqn%")
    List<CodeIndex> findByGithubRepositoryAndFqnContaining(
            @Param("repo") GithubRepository repository,
            @Param("partialFqn") String partialFqn);

    // 파일 경로 정확 매칭 (Gemini Function Calling: get_file_source)
    Optional<CodeIndex> findByGithubRepositoryAndFilePath(GithubRepository repository, String filePath);

    // 파일 경로 부분 매칭
    @Query("select c from CodeIndex c where c.githubRepository = :repo " +
           "and c.filePath like %:partialPath%")
    List<CodeIndex> findByGithubRepositoryAndFilePathContaining(
            @Param("repo") GithubRepository repository,
            @Param("partialPath") String partialPath);

    // repo 전체 삭제 (repo 제거 시)
    void deleteByGithubRepository(GithubRepository repository);

    // ID 기반 일괄 삭제 (detached entity 없이 안전하게 삭제)
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from CodeIndex c where c.githubRepository.id = :repoId")
    void deleteByGithubRepositoryId(@org.springframework.data.repository.query.Param("repoId") Long repoId);
}
