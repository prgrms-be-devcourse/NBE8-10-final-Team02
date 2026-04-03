package com.back.backend.domain.activity.dto;

public record ScoreTrendEntry(
        Long sessionId,
        int score,
        String completedAt
) {
}
