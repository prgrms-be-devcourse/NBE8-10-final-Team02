package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {

    // 사용자당 GitHub 연결은 1개만 허용 (uk_github_connections_user)
    Optional<GithubConnection> findByUser(User user);

    boolean existsByUser(User user);
}