package com.back.backend.domain.interview.dto.response;

import com.back.backend.domain.interview.service.InterviewQuestionSetJobStatus;
import com.back.backend.domain.interview.service.InterviewQuestionSetJobStore;

/**
 * 면접 질문 세트 AI 생성 작업 상태 응답.
 * POST /question-sets 즉시 반환 및 GET /question-sets/status/{jobId} 폴링 시 반환된다.
 * COMPLETED 시 questionSetId가 채워진다.
 */
public record QuestionSetJobStatusResponse(
        String jobId,
        String status,
        Long questionSetId,
        String error
) {
    public static QuestionSetJobStatusResponse pending(String jobId) {
        return new QuestionSetJobStatusResponse(jobId, InterviewQuestionSetJobStatus.PENDING.name(), null, null);
    }

    public static QuestionSetJobStatusResponse from(InterviewQuestionSetJobStore.JobData data) {
        return new QuestionSetJobStatusResponse(
                data.jobId(),
                data.status().name(),
                data.questionSetId(),
                data.error()
        );
    }
}
