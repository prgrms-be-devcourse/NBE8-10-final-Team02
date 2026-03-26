package com.back.backend.domain.interview.controller;

import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.dto.request.SubmitInterviewAnswerRequest;
import com.back.backend.domain.interview.dto.response.InterviewAnswerSubmitResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.service.InterviewAnswerService;
import com.back.backend.domain.interview.service.InterviewSessionService;
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
@RequestMapping("/api/v1/interview/sessions")
@RequiredArgsConstructor
public class InterviewSessionController {

    private final InterviewAnswerService interviewAnswerService;
    private final InterviewSessionService interviewSessionService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewSessionResponse> startSession(
            Authentication authentication,
            @RequestBody StartInterviewSessionRequest request
    ) {
        return ApiResponse.success(
                interviewSessionService.startSession(
                        currentUserResolver.resolveUserId(authentication),
                        request
                )
        );
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewSessionDetailResponse> getSessionDetail(
            Authentication authentication,
            @PathVariable long sessionId
    ) {
        return ApiResponse.success(
                interviewSessionService.getSessionDetail(
                        currentUserResolver.resolveUserId(authentication),
                        sessionId
                )
        );
    }

    @PostMapping("/{sessionId}/pause")
    public ApiResponse<InterviewSessionTransitionResponse> pauseSession(
            Authentication authentication,
            @PathVariable long sessionId
    ) {
        return ApiResponse.success(
                interviewSessionService.pauseSession(
                        currentUserResolver.resolveUserId(authentication),
                        sessionId
                )
        );
    }

    @PostMapping("/{sessionId}/resume")
    public ApiResponse<InterviewSessionTransitionResponse> resumeSession(
            Authentication authentication,
            @PathVariable long sessionId
    ) {
        return ApiResponse.success(
                interviewSessionService.resumeSession(
                        currentUserResolver.resolveUserId(authentication),
                        sessionId
                )
        );
    }

    @PostMapping("/{sessionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewAnswerSubmitResponse> submitAnswer(
            Authentication authentication,
            @PathVariable long sessionId,
            @RequestBody SubmitInterviewAnswerRequest request
    ) {
        return ApiResponse.success(
                interviewAnswerService.submitAnswer(
                        currentUserResolver.resolveUserId(authentication),
                        sessionId,
                        request
                )
        );
    }
}
