package com.back.backend.domain.interview.controller;

import com.back.backend.domain.ai.service.InterviewQuestionsGenerateService;
import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.request.CreateQuestionSetRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetDetailResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetSummaryResponse;
import com.back.backend.domain.interview.entity.DifficultyLevel;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/interview/question-sets")
@RequiredArgsConstructor
public class InterviewQuestionSetController {

    private final InterviewQuestionSetService interviewQuestionSetService;
    private final InterviewQuestionsGenerateService interviewQuestionsGenerateService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionSetSummaryResponse> createQuestionSet(
        Authentication authentication,
        @RequestBody CreateQuestionSetRequest request
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);

        QuestionSetSummaryResponse response = interviewQuestionsGenerateService.generate(
            userId,
            request.applicationId(),
            request.title(),
            request.questionCount(),
            parseDifficultyLevel(request.difficultyLevel()),
            request.questionTypes()
        );

        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<QuestionSetSummaryResponse>> getQuestionSets(Authentication authentication) {
        long userId = currentUserResolver.resolveUserId(authentication);
        return ApiResponse.success(interviewQuestionsGenerateService.getQuestionSets(userId));
    }

    @GetMapping("/{questionSetId}")
    public ApiResponse<QuestionSetDetailResponse> getQuestionSet(
        Authentication authentication,
        @PathVariable long questionSetId
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        return ApiResponse.success(interviewQuestionsGenerateService.getQuestionSet(userId, questionSetId));
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

    private DifficultyLevel parseDifficultyLevel(String value) {
        return Arrays.stream(DifficultyLevel.values())
            .filter(e -> e.getValue().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown difficultyLevel: " + value
            ));
    }
}
