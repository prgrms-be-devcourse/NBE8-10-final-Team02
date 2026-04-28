package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.service.InterviewQuestionsGenerateService;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.interview.dto.response.QuestionSetSummaryResponse;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 면접 질문 세트 AI 생성을 비동기로 실행하는 서비스.
 *
 * submitAsync()는 jobId를 반환하고 즉시 리턴한다.
 * 실제 AI 호출은 aiTaskExecutor 스레드에서 실행되며,
 * 진행 상태는 InterviewQuestionSetJobStore(Redis)에 기록된다.
 * 클라이언트는 GET /question-sets/status/{jobId} 폴링으로 완료 여부를 확인한다.
 */
@Service
public class AsyncInterviewQuestionsGenerateService {

    private static final Logger log = LoggerFactory.getLogger(AsyncInterviewQuestionsGenerateService.class);

    private final InterviewQuestionsGenerateService interviewQuestionsGenerateService;
    private final InterviewQuestionSetJobStore jobStore;
    private final ApplicationRepository applicationRepository;
    private final Executor aiTaskExecutor;

    public AsyncInterviewQuestionsGenerateService(
            InterviewQuestionsGenerateService interviewQuestionsGenerateService,
            InterviewQuestionSetJobStore jobStore,
            ApplicationRepository applicationRepository,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor
    ) {
        this.interviewQuestionsGenerateService = interviewQuestionsGenerateService;
        this.jobStore = jobStore;
        this.applicationRepository = applicationRepository;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * applicationId가 userId에게 속하는지 검증한다.
     * 비동기 제출 전에 호출하여 잘못된 작업이 큐에 들어가지 않도록 한다.
     */
    public void validateOwnership(long userId, long applicationId) {
        applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.APPLICATION_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "지원 준비를 찾을 수 없습니다."
                ));
    }

    /**
     * 질문 세트 AI 생성 작업을 비동기로 제출한다.
     *
     * @return jobId — 클라이언트가 폴링에 사용하는 UUID
     */
    public String submitAsync(
            long userId,
            long applicationId,
            String title,
            int questionCount,
            DifficultyLevel difficultyLevel,
            List<String> questionTypes
    ) {
        String jobId = jobStore.create(userId);

        aiTaskExecutor.execute(() -> {
            jobStore.setInProgress(userId, jobId);
            try {
                QuestionSetSummaryResponse result = interviewQuestionsGenerateService.generate(
                        userId, applicationId, title, questionCount, difficultyLevel, questionTypes
                );
                jobStore.setCompleted(userId, jobId, result.questionSetId());
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
                jobStore.setFailed(userId, jobId, error);
                log.warn("[QsGen] 생성 실패: userId={}, jobId={}, error={}", userId, jobId, error);
            }
        });

        return jobId;
    }
}
