package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.RepoSummary;
import com.back.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepoSummaryRepository extends JpaRepository<RepoSummary, Long> {

    // 최신 버전 조회 (summary_version desc)
    Optional<RepoSummary> findTopByUserAndGithubRepositoryOrderBySummaryVersionDesc(
            User user, GithubRepository githubRepository);

    // 사용자의 전체 repo 최신 요약 목록 (MergedSummary 집계용)
    List<RepoSummary> findAllByUserOrderBySummaryVersionDesc(User user);

    // repo별 모든 버전 (버전 이력 조회용)
    List<RepoSummary> findByUserAndGithubRepositoryOrderBySummaryVersionDesc(
            User user, GithubRepository githubRepository);

    // repo 제거 시 해당 요약 삭제
    void deleteByGithubRepository(GithubRepository githubRepository);
}
