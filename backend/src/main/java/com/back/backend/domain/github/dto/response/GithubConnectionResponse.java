package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.entity.GithubConnection;

import java.time.Instant;

/**
 * POST /github/connections 응답 데이터.
 * Entity를 직접 노출하지 않고 필요한 필드만 매핑한다.
 */
public record GithubConnectionResponse(
        Long id,
        Long userId,
        Long githubUserId,
        String githubLogin,
        String accessScope,
        String syncStatus,
        Instant connectedAt,
        Instant lastSyncedAt
) {

    public static GithubConnectionResponse from(GithubConnection connection) {
        return new GithubConnectionResponse(
                connection.getId(),
                connection.getUser().getId(),
                connection.getGithubUserId(),
                connection.getGithubLogin(),
                connection.getAccessScope(),
                connection.getSyncStatus().getValue(),
                connection.getConnectedAt(),
                connection.getLastSyncedAt()
        );
    }
}