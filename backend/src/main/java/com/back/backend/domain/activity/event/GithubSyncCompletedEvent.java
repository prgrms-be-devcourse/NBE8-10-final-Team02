package com.back.backend.domain.activity.event;

public record GithubSyncCompletedEvent(Long userId, Long repositoryId, int newCommitCount) {
}
