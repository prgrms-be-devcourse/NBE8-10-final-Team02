package com.back.backend.domain.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 면접 결과 생성을 비동기로 실행하는 서비스
 *
 * CompletableFuture를 반환하여 호출자가 동기/비동기 완료를 구분할 수 있음:
 * - 동기 완료 (테스트, CallerRunsPolicy): future.isDone() == true → 원래 응답 반환
 * - 비동기 완료 (프로덕션): future.isDone() == false → 즉시 반환 + 폴링
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
     * AI 결과 생성을 비동기로 제출하고 CompletableFuture를 반환
     *
     * 호출자는 future.isDone()으로 동기 완료 여부를 확인하여
     * 동기 시 원래 응답을, 비동기 시 즉시 반환을 선택할 수 있음
     */
    public CompletableFuture<InterviewResultGenerationService.GeneratedInterviewResult> submitAsync(
            long userId,
            long sessionId,
            long questionSetId,
            List<com.back.backend.domain.interview.entity.InterviewAnswer> answers,
            String jobRole
    ) {
        CompletableFuture<InterviewResultGenerationService.GeneratedInterviewResult> future =
                new CompletableFuture<>();

        aiTaskExecutor.execute(() -> {
            try {
                InterviewResultGenerationService.GeneratedInterviewResult generatedResult =
                        interviewResultGenerationService.generate(
                                userId, sessionId, questionSetId, answers, jobRole
                        );
                future.complete(generatedResult);
            } catch (Exception exception) {
                log.warn("[AsyncResultGenerator] 세션({}) AI 결과 생성 실패: {}", sessionId, exception.getMessage());
                future.completeExceptionally(exception);
            }
        });

        return future;
    }
}
