package com.back.backend.domain.interview.service;

import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * AI 면접 결과 생성을 비동기로 실행하는 서비스
 *
 * completeSession() 호출 시 세션 상태를 COMPLETED로 즉시 전환한 뒤,
 * 무거운 AI 평가 작업은 aiTaskExecutor에서 비동기로 실행하여
 * HTTP 응답을 즉시 반환할 수 있게 함.
 *
 * 실패 시 retryPendingResultGenerationIfNeeded()가
 * GET /result 폴링 시 자동 재시도함
 */
@Service
public class AsyncInterviewResultGenerator {

    private static final Logger log = LoggerFactory.getLogger(AsyncInterviewResultGenerator.class);

    private final InterviewResultGenerationService interviewResultGenerationService;
    private final Executor aiTaskExecutor;

    public AsyncInterviewResultGenerator(
            InterviewResultGenerationService interviewResultGenerationService,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor
    ) {
        this.interviewResultGenerationService = interviewResultGenerationService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * AI 결과 생성을 비동기로 제출
     * 결과 생성 + DB 저장은 callback을 통해 호출자가 제어함
     *
     * @param userId      사용자 ID
     * @param sessionId   세션 ID
     * @param questionSetId 질문 세트 ID
     * @param answers     답변 목록
     * @param jobRole     직무
     * @param callback    AI 생성 결과를 받아 DB에 저장하는 콜백
     * @param onFailure   실패 시 호출되는 콜백
     */
    public void submitAsync(
            long userId,
            long sessionId,
            long questionSetId,
            java.util.List<com.back.backend.domain.interview.entity.InterviewAnswer> answers,
            String jobRole,
            ResultGenerationCallback callback,
            ResultGenerationFailureHandler onFailure
    ) {
        aiTaskExecutor.execute(() -> {
            try {
                InterviewResultGenerationService.GeneratedInterviewResult generatedResult =
                        interviewResultGenerationService.generate(
                                userId, sessionId, questionSetId, answers, jobRole
                        );
                callback.onSuccess(generatedResult);
            } catch (ServiceException exception) {
                log.warn("[AsyncResultGenerator] 세션({}) AI 결과 생성 실패: {}", sessionId, exception.getMessage());
                onFailure.onFailure(sessionId, exception);
            } catch (RuntimeException exception) {
                log.error("[AsyncResultGenerator] 세션({}) AI 결과 생성 중 예상치 못한 오류", sessionId, exception);
                onFailure.onUnexpectedFailure(sessionId);
            }
        });
    }

    @FunctionalInterface
    public interface ResultGenerationCallback {
        void onSuccess(InterviewResultGenerationService.GeneratedInterviewResult generatedResult);
    }

    public interface ResultGenerationFailureHandler {
        void onFailure(long sessionId, ServiceException exception);
        void onUnexpectedFailure(long sessionId);
    }
}
