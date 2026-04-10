package com.back.backend.domain.application.controller;

import com.back.backend.domain.application.dto.request.GenerateAnswersRequest;
import com.back.backend.domain.application.dto.response.ApplicationAiGenerationStatusResponse;
import com.back.backend.domain.application.service.ApplicationAiGenerationJobStore;
import com.back.backend.domain.application.service.AsyncSelfIntroGenerateService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationAiController {

    private final AsyncSelfIntroGenerateService asyncSelfIntroGenerateService;
    private final ApplicationAiGenerationJobStore jobStore;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 자소서 AI 답변 생성을 비동기로 시작한다.
     *
     * <p>소유권 검증 후 작업을 applicationAiTaskExecutor 큐에 밀어 넣고
     * 즉시 202 Accepted를 반환한다.</p>
     *
     * <p>이미 PENDING/IN_PROGRESS 상태면 중복 제출 없이 현재 상태를 202로 반환한다.</p>
     *
     * <p>진행 상황은 GET .../generate-answers/status 폴링으로 확인한다.</p>
     */
    @PostMapping("/{applicationId}/questions/generate-answers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ApplicationAiGenerationStatusResponse> generateAnswers(
            Authentication authentication,
            @PathVariable long applicationId,
            @RequestBody GenerateAnswersRequest request
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        asyncSelfIntroGenerateService.validateOwnership(userId, applicationId);
        asyncSelfIntroGenerateService.submitAsync(userId, applicationId, request.regenerate());

        ApplicationAiGenerationStatusResponse response = jobStore.get(userId, applicationId)
                .map(data -> ApplicationAiGenerationStatusResponse.from(applicationId, data))
                .orElseGet(() -> ApplicationAiGenerationStatusResponse.pending(applicationId));

        return ApiResponse.success(response);
    }

    /**
     * 자소서 AI 답변 생성 진행 상태를 조회한다.
     *
     * <p>클라이언트는 이 API를 주기적으로 폴링하여 COMPLETED/FAILED 상태를 확인한다.
     * TTL 만료 또는 미요청이면 data: null 반환.</p>
     */
    @GetMapping("/{applicationId}/questions/generate-answers/status")
    public ApiResponse<ApplicationAiGenerationStatusResponse> getGenerateAnswersStatus(
            Authentication authentication,
            @PathVariable long applicationId
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        ApplicationAiGenerationStatusResponse response = jobStore.get(userId, applicationId)
                .map(data -> ApplicationAiGenerationStatusResponse.from(applicationId, data))
                .orElse(null);

        return ApiResponse.success(response);
    }
}
