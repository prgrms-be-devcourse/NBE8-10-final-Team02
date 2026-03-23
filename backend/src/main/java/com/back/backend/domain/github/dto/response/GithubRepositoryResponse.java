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
        String visibility,   // "public" | "private" | "internal"
        String defaultBranch,
        boolean isSelected
) {

    public static GithubRepositoryResponse from(GithubRepository repo) {
        return new GithubRepositoryResponse(
                repo.getId(),
                repo.getGithubRepoId(),
                repo.getOwnerLogin(),
                repo.getRepoName(),
                repo.getFullName(),
                repo.getHtmlUrl(),
                repo.getVisibility().getValue(),
                repo.getDefaultBranch(),
                repo.isSelected()
        );
    }
}