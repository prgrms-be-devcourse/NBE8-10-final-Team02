package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {

    // 사용자당 GitHub 연결은 1개만 허용 (uk_github_connections_user)
    Optional<GithubConnection> findByUser(User user);

    boolean existsByUser(User user);

    // GitHub 계정 ID로 연결 조회 — 다른 app 사용자가 같은 GitHub 계정을 이미 연동했는지 확인하는 데 사용
    Optional<GithubConnection> findByGithubUserId(Long githubUserId);
}