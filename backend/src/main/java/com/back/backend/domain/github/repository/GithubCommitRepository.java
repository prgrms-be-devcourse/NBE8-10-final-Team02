package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GithubCommitRepository extends JpaRepository<GithubCommit, Long> {

    // upsert 전 중복 여부 확인 (uk_github_commits_repository_sha)
    Optional<GithubCommit> findByRepositoryAndGithubCommitSha(GithubRepository repository, String sha);

    // significance check 용: 특정 시점 이후의 사용자 본인 커밋 조회
    List<GithubCommit> findByRepositoryAndUserCommitTrueAndCommittedAtAfter(
            GithubRepository repository, Instant since);

    // significance check 용: 최초 분석 시 사용자 본인 커밋 전체 조회
    List<GithubCommit> findByRepositoryAndUserCommitTrue(GithubRepository repository);

    // 파이프라인 author email 조회: 본인 커밋의 distinct author_email 목록 (최신 커밋 기준)
    @Query("select distinct c.authorEmail from GithubCommit c " +
           "where c.repository = :repo and c.userCommit = true and c.authorEmail is not null")
    List<String> findDistinctAuthorEmailsByRepositoryAndUserCommitTrue(@Param("repo") GithubRepository repo);

    // 커밋 존재 여부 (단건)
    boolean existsByRepository(GithubRepository repository);

    // 커밋이 1건 이상 있는 repositoryId 집합 — getRepositories() 응답에 hasCommits 포함 시 N+1 방지
    @Query("select distinct c.repository.id from GithubCommit c where c.repository.id in :repoIds")
    Set<Long> findRepositoryIdsWithCommits(@Param("repoIds") List<Long> repoIds);
}