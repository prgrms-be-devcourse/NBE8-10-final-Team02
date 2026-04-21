package com.back.backend.domain.application.service;

import com.back.backend.domain.ai.service.SelfIntroGenerateService;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * 자소서 AI 답변 생성을 비동기로 실행하는 서비스.
 *
 * <p>submitAsync()는 즉시 반환하고, 실제 AI 호출은 applicationAiTaskExecutor 스레드에서 실행된다.
 * 진행 상태는 ApplicationAiGenerationJobStore(Redis)에 기록되며,
 * 클라이언트는 GET .../generate-answers/status 폴링으로 완료 여부를 확인한다.</p>
 */
@Service
@SuppressWarnings("deprecation")
public class AsyncSelfIntroGenerateService {

    private static final Logger log = LoggerFactory.getLogger(AsyncSelfIntroGenerateService.class);


    private final SelfIntroGenerateService selfIntroGenerateService;
    private final ApplicationAiGenerationJobStore jobStore;
    private final ApplicationRepository applicationRepository;
    private final Executor applicationAiTaskExecutor;

    public AsyncSelfIntroGenerateService(
            SelfIntroGenerateService selfIntroGenerateService,
            ApplicationAiGenerationJobStore jobStore,
            ApplicationRepository applicationRepository,
            @Qualifier("aiTaskExecutor") Executor applicationAiTaskExecutor
    ) {
        this.selfIntroGenerateService = selfIntroGenerateService;
        this.jobStore = jobStore;
        this.applicationRepository = applicationRepository;
        this.applicationAiTaskExecutor = applicationAiTaskExecutor;
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
     * AI 자소서 생성 작업을 비동기로 제출한다.
     *
     * <p>이미 PENDING/IN_PROGRESS 상태이면 중복 제출 없이 idempotent하게 반환한다.
     * 소유권 검증은 {@link #validateOwnership(long, long)}을 먼저 호출해야 한다.</p>
     */
    public void submitAsync(long userId, long applicationId, boolean regenerate) {
        ApplicationAiGenerationJobStore.JobData existing = jobStore.get(userId, applicationId).orElse(null);
        if (existing != null && isInFlight(existing.status())) {
            log.info("[AppAI] 이미 진행 중 — skip: userId={}, applicationId={}, status={}",
                    userId, applicationId, existing.status());
            return;
        }

        jobStore.setPending(userId, applicationId);

        applicationAiTaskExecutor.execute(() -> {
            jobStore.setInProgress(userId, applicationId);
            try {
                selfIntroGenerateService.generate(userId, applicationId, regenerate);
                jobStore.setCompleted(userId, applicationId);
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
                jobStore.setFailed(userId, applicationId, error);
                log.warn("[AppAI] 생성 실패: userId={}, applicationId={}, error={}", userId, applicationId, error);
            }
        });
    }

    private boolean isInFlight(ApplicationAiGenerationJobStatus status) {
        return status == ApplicationAiGenerationJobStatus.PENDING
                || status == ApplicationAiGenerationJobStatus.IN_PROGRESS;
    }
}
