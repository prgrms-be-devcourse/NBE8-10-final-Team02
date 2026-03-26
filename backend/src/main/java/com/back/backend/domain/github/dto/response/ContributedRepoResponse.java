package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.service.GithubApiClient.GithubContributedRepo;

public record ContributedRepoResponse(
        Long githubRepoId,
        String nameWithOwner,
        String url,
        String language,
        Integer repoSizeKb,
        int contributionCount,
        boolean alreadySaved,
        Long repositoryId    // github_repositories.id (저장된 경우에만 non-null)
) {
    public static ContributedRepoResponse from(GithubContributedRepo repo, GithubRepository saved) {
        boolean alreadySaved = saved != null;
        return new ContributedRepoResponse(
                repo.githubRepoId(),
                repo.nameWithOwner(),
                repo.url(),
                repo.language(),
                repo.repoSizeKb(),
                repo.contributionCount(),
                alreadySaved,
                alreadySaved ? saved.getId() : null
        );
    }
}
