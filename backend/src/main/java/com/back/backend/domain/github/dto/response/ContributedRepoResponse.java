package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.service.GithubApiClient.GithubContributedRepo;

public record ContributedRepoResponse(
        Long githubRepoId,
        String nameWithOwner,
        String url,
        String language,
        Integer repoSizeKb,
        int contributionCount,
        boolean alreadySaved
) {
    public static ContributedRepoResponse from(GithubContributedRepo repo, boolean alreadySaved) {
        return new ContributedRepoResponse(
                repo.githubRepoId(),
                repo.nameWithOwner(),
                repo.url(),
                repo.language(),
                repo.repoSizeKb(),
                repo.contributionCount(),
                alreadySaved
        );
    }
}
