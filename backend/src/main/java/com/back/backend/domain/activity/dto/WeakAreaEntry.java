package com.back.backend.domain.activity.dto;

public record WeakAreaEntry(
        String tagName,
        String category,
        double avgScore,
        int count
) {
}
