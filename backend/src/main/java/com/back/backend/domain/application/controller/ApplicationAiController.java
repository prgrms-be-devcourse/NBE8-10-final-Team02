package com.back.backend.domain.application.controller;

import com.back.backend.domain.ai.service.SelfIntroGenerateService;
import com.back.backend.domain.ai.service.SelfIntroGenerateService.GenerateResult;
import com.back.backend.domain.application.dto.request.GenerateAnswersRequest;
import com.back.backend.domain.application.dto.response.ApplicationAnswerGenerationResponse;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationAiController {

    private final SelfIntroGenerateService selfIntroGenerateService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{applicationId}/questions/generate-answers")
    public ApiResponse<ApplicationAnswerGenerationResponse> generateAnswers(
        Authentication authentication,
        @PathVariable long applicationId,
        @RequestBody GenerateAnswersRequest request
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);

        GenerateResult result = selfIntroGenerateService.generate(
            userId, applicationId, request.regenerate()
        );

        return ApiResponse.success(
            ApplicationAnswerGenerationResponse.of(
                applicationId,
                request.regenerate(),
                result.generatedCount(),
                result.allQuestions()
            )
        );
    }
}
