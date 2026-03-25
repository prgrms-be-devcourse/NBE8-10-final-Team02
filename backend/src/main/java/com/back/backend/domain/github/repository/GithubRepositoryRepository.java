package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    // 특정 연결의 repo 목록 (페이지네이션)
    Page<GithubRepository> findByGithubConnection(GithubConnection connection, Pageable pageable);

    // 선택 상태로 필터링 (is_selected 인덱스 활용)
    Page<GithubRepository> findByGithubConnectionAndSelected(GithubConnection connection, boolean selected, Pageable pageable);

    // upsert 전 중복 여부 확인 (uk_github_repositories_connection_repo)
    Optional<GithubRepository> findByGithubConnectionAndGithubRepoId(GithubConnection connection, Long githubRepoId);

    // 사용자가 선택한 repo들 일괄 조회 (selection 저장 시 사용)
    List<GithubRepository> findByGithubConnectionAndIdIn(GithubConnection connection, List<Long> ids);

    // 특정 연결의 선택된 repo 전체 조회 (커밋 동기화 시 사용)
    List<GithubRepository> findByGithubConnectionAndSelectedTrue(GithubConnection connection);

    // 이미 저장된 githubRepoId 집합 조회 (contributions discovered 중복 체크용)
    @Query("select r.githubRepoId from GithubRepository r where r.githubConnection = :connection and r.githubRepoId in :repoIds")
    Set<Long> findGithubRepoIdsByGithubConnectionAndGithubRepoIdIn(
            @Param("connection") GithubConnection connection,
            @Param("repoIds") List<Long> repoIds
    );

    // githubConnection + user 를 JOIN FETCH — 트랜잭션 없는 비동기 컨텍스트에서 LazyInitializationException 방지
    @Query("SELECT r FROM GithubRepository r JOIN FETCH r.githubConnection gc JOIN FETCH gc.user WHERE r.id = :id")
    Optional<GithubRepository> findByIdWithConnection(@Param("id") Long id);
}