package com.back.backend.domain.application.dto.request;

public record CreateApplicationRequest(
        String applicationTitle,
        String companyName,
        String jobRole,
        String applicationType
) {
}
