package com.back.backend.domain.application.dto.response;

import com.back.backend.domain.application.service.ApplicationAiGenerationJobStatus;
import com.back.backend.domain.application.service.ApplicationAiGenerationJobStore;

/**
 * 자소서 AI 생성 작업 상태 응답.
 * GET .../generate-answers/status 폴링 시 반환된다.
 */
public record ApplicationAiGenerationStatusResponse(
        long applicationId,
        String status,
        String error
) {
    public static ApplicationAiGenerationStatusResponse pending(long applicationId) {
        return new ApplicationAiGenerationStatusResponse(
                applicationId, ApplicationAiGenerationJobStatus.PENDING.name(), null);
    }

    public static ApplicationAiGenerationStatusResponse from(
            long applicationId, ApplicationAiGenerationJobStore.JobData data) {
        return new ApplicationAiGenerationStatusResponse(
                applicationId, data.status().name(), data.error());
    }
}
