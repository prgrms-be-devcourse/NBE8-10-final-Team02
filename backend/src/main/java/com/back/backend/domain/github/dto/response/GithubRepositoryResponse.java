package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.entity.GithubRepository;

/**
 * GET /github/repositories 응답의 개별 repo 항목.
 */
public record GithubRepositoryResponse(
        Long id,
        Long githubRepoId,
        String ownerLogin,
        String repoName,
        String fullName,
        String htmlUrl,
        String visibility,      // "public" | "private" | "internal"
        String defaultBranch,
        boolean isSelected,
        boolean hasCommits,     // 커밋 동기화 완료 여부 (false면 포트폴리오 분석 불가)
        RepoSyncStatusResponse analysisStatus  // 분석 상태 (분석 미요청 또는 TTL 만료 시 null)
) {

    /** 분석 상태 없이 생성 (기여 repo 저장 등 분석 상태가 불필요한 컨텍스트) */
    public static GithubRepositoryResponse from(GithubRepository repo) {
        return from(repo, false, null);
    }

    public static GithubRepositoryResponse from(GithubRepository repo, boolean hasCommits) {
        return from(repo, hasCommits, null);
    }

    public static GithubRepositoryResponse from(GithubRepository repo, boolean hasCommits,
                                                RepoSyncStatusResponse analysisStatus) {
        return new GithubRepositoryResponse(
                repo.getId(),
                repo.getGithubRepoId(),
                repo.getOwnerLogin(),
                repo.getRepoName(),
                repo.getFullName(),
                repo.getHtmlUrl(),
                repo.getVisibility().getValue(),
                repo.getDefaultBranch(),
                repo.isSelected(),
                hasCommits,
                analysisStatus
        );
    }
}