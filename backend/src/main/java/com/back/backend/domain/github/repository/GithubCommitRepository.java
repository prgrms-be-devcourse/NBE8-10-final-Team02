package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubCommitRepository extends JpaRepository<GithubCommit, Long> {

    // upsert 전 중복 여부 확인 (uk_github_commits_repository_sha)
    Optional<GithubCommit> findByRepositoryAndGithubCommitSha(GithubRepository repository, String sha);
}