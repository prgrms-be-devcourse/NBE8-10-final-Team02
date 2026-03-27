package com.back.backend.domain.interview.controller;

import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.request.CreateInterviewQuestionSetRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionSetDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionSetSummaryResponse;
import com.back.backend.domain.interview.service.InterviewQuestionSetService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/interview/question-sets")
@RequiredArgsConstructor
public class InterviewQuestionSetController {

    private final InterviewQuestionSetService interviewQuestionSetService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewQuestionSetSummaryResponse> createQuestionSet(
            Authentication authentication,
            @RequestBody CreateInterviewQuestionSetRequest request
    ) {
        return ApiResponse.success(
                interviewQuestionSetService.createQuestionSet(
                        currentUserResolver.resolveUserId(authentication),
                        request
                )
        );
    }

    @GetMapping
    public ApiResponse<List<InterviewQuestionSetSummaryResponse>> getQuestionSets(Authentication authentication) {
        return ApiResponse.success(
                interviewQuestionSetService.getQuestionSets(currentUserResolver.resolveUserId(authentication))
        );
    }

    @GetMapping("/{questionSetId}")
    public ApiResponse<InterviewQuestionSetDetailResponse> getQuestionSetDetail(
            Authentication authentication,
            @PathVariable long questionSetId
    ) {
        return ApiResponse.success(
                interviewQuestionSetService.getQuestionSetDetail(
                        currentUserResolver.resolveUserId(authentication),
                        questionSetId
                )
        );
    }

    @PostMapping("/{questionSetId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterviewQuestionResponse> addQuestion(
            Authentication authentication,
            @PathVariable long questionSetId,
            @RequestBody AddInterviewQuestionRequest request
    ) {
        return ApiResponse.success(
                interviewQuestionSetService.addQuestion(
                        currentUserResolver.resolveUserId(authentication),
                        questionSetId,
                        request
                )
        );
    }

    @DeleteMapping("/{questionSetId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            Authentication authentication,
            @PathVariable long questionSetId,
            @PathVariable long questionId
    ) {
        interviewQuestionSetService.deleteQuestion(
                currentUserResolver.resolveUserId(authentication),
                questionSetId,
                questionId
        );
        return ResponseEntity.noContent().build();
    }
}
