package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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
}