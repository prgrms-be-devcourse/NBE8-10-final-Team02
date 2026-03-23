package com.back.backend.domain.application.dto.request;

public record UpdateApplicationRequest(
        String applicationTitle,
        String companyName,
        String jobRole,
        String status,
        String applicationType
) {
}
