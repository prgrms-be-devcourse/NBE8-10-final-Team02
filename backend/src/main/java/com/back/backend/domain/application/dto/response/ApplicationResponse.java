package com.back.backend.domain.application.dto.response;

import java.time.Instant;

public record ApplicationResponse(
        Long id,
        String applicationTitle,
        String companyName,
        String jobRole,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String applicationType
) {
}
