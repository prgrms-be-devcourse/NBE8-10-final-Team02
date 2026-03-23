package com.back.backend.domain.github.dto.response;

import java.time.Instant;

/**
 * POST /github/repositories/{repositoryId}/sync-commits 응답 데이터.
 * 동기화가 시작됐음을 알린다 (202 Accepted).
 */
public record RepositorySyncResponse(
        Long repositoryId,
        String syncStatus,  // "queued" (동기화 시작됨)
        Instant queuedAt
) {

    public static RepositorySyncResponse queued(Long repositoryId) {
        return new RepositorySyncResponse(repositoryId, "queued", Instant.now());
    }
}