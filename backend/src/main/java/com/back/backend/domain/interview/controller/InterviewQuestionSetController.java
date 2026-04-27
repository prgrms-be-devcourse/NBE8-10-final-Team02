package com.back.backend.domain.interview.controller;

import com.back.backend.domain.ai.service.InterviewQuestionsGenerateService;
import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.request.CreateQuestionSetRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetDetailResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetJobStatusResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetSummaryResponse;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.service.AsyncInterviewQuestionsGenerateService;
import com.back.backend.domain.interview.service.InterviewQuestionSetJobStore;
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
    private final AsyncInterviewQuestionsGenerateService asyncInterviewQuestionsGenerateService;
    private final InterviewQuestionSetJobStore interviewQuestionSetJobStore;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 면접 질문 세트 AI 생성을 비동기로 시작한다.
     *
     * 즉시 202 Accepted를 반환하고, 실제 생성은 백그라운드에서 진행된다.
     * 진행 상황은 GET /question-sets/status/{jobId} 폴링으로 확인한다.
     * COMPLETED 시 응답의 questionSetId로 GET /question-sets/{questionSetId} 호출 가능.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<QuestionSetJobStatusResponse> createQuestionSet(
        Authentication authentication,
        @RequestBody CreateQuestionSetRequest request
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);

        String jobId = asyncInterviewQuestionsGenerateService.submitAsync(
            userId,
            request.applicationId(),
            request.title(),
            request.questionCount(),
            parseDifficultyLevel(request.difficultyLevel()),
            request.questionTypes()
        );

        QuestionSetJobStatusResponse response = interviewQuestionSetJobStore.get(userId, jobId)
                .map(QuestionSetJobStatusResponse::from)
                .orElseGet(() -> QuestionSetJobStatusResponse.pending(jobId));

        return ApiResponse.success(response);
    }

    /**
     * 면접 질문 세트 AI 생성 진행 상태를 조회한다.
     *
     * COMPLETED 시 questionSetId가 채워진다.
     * TTL 만료 또는 미요청이면 data: null 반환.
     */
    @GetMapping("/status/{jobId}")
    public ApiResponse<QuestionSetJobStatusResponse> getQuestionSetJobStatus(
        Authentication authentication,
        @PathVariable String jobId
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        QuestionSetJobStatusResponse response = interviewQuestionSetJobStore.get(userId, jobId)
                .map(QuestionSetJobStatusResponse::from)
                .orElse(null);
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
