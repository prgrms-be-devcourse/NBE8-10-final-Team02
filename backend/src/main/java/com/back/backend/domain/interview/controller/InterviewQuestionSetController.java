package com.back.backend.domain.interview.controller;

import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.service.InterviewQuestionSetService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interview/question-sets")
@RequiredArgsConstructor
public class InterviewQuestionSetController {

    private final InterviewQuestionSetService interviewQuestionSetService;
    private final CurrentUserResolver currentUserResolver;

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
}
