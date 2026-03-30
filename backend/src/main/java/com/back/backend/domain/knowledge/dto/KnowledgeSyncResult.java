package com.back.backend.domain.knowledge.dto;

public record KnowledgeSyncResult(
        int imported,
        int updated,
        int skipped,
        int failed,
        int deleted
) {}
