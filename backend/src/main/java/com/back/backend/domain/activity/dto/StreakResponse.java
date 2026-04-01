package com.back.backend.domain.activity.dto;

public record StreakResponse(
        int currentStreak,
        int longestStreak
) {
}
