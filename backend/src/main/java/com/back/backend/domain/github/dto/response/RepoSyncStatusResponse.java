package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.analysis.SyncStatus;
import com.back.backend.domain.github.analysis.SyncStatusService;

import java.time.Instant;

/**
 * GET /github/repositories/{repositoryId}/sync-status 응답.
 * Redis SyncStatusData → HTTP 응답 변환용 DTO.
 */
public record RepoSyncStatusResponse(
        Long repositoryId,
        SyncStatus status,
        String step,
        Instant startedAt,
        Instant estimatedEndAt,
        Instant completedAt,
        String error,
        String skipReason
) {
    public static RepoSyncStatusResponse from(SyncStatusService.SyncStatusData data) {
        return new RepoSyncStatusResponse(
                data.repositoryId(),
                data.status(),
                data.step(),
                data.startedAt(),
                data.estimatedEndAt(),
                data.completedAt(),
                data.error(),
                data.skipReason()
        );
    }
}
