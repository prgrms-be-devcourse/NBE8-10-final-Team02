package com.back.backend.domain.practice.controller;

import com.back.backend.domain.practice.dto.request.SubmitPracticeAnswerRequest;
import com.back.backend.domain.practice.dto.response.PracticeQuestionResponse;
import com.back.backend.domain.practice.dto.response.PracticeSessionDetailResponse;
import com.back.backend.domain.practice.dto.response.PracticeSessionResponse;
import com.back.backend.domain.practice.dto.response.PracticeTagResponse;
import com.back.backend.domain.practice.service.PracticeQuestionService;
import com.back.backend.domain.practice.service.PracticeSessionService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.response.Pagination;
import com.back.backend.global.security.auth.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeQuestionService questionService;
    private final PracticeSessionService sessionService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/questions")
    public ApiResponse<List<PracticeQuestionResponse>> getQuestions(
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<PracticeQuestionResponse> result = questionService.getQuestions(
                tagIds, questionType, PageRequest.of(page, size, Sort.by("id")));
        Pagination pagination = new Pagination(
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
        return ApiResponse.success(result.getContent(), pagination);
    }

    @GetMapping("/questions/random")
    public ApiResponse<List<PracticeQuestionResponse>> getRandomQuestions(
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "1") int count
    ) {
        int safeCount = Math.min(Math.max(count, 1), 10);
        return ApiResponse.success(questionService.getRandomQuestions(tagIds, questionType, safeCount));
    }

    @GetMapping("/tags")
    public ApiResponse<List<PracticeTagResponse>> getTags(
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(questionService.getTags(category));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PracticeSessionResponse> submitAndEvaluate(
            Authentication authentication,
            @Valid @RequestBody SubmitPracticeAnswerRequest request
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        return ApiResponse.success(sessionService.submitAndEvaluate(userId, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<PracticeSessionResponse>> getSessions(
            Authentication authentication,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        Page<PracticeSessionResponse> result = sessionService.getSessions(
                userId, questionType, PageRequest.of(page, size));
        Pagination pagination = new Pagination(
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
        return ApiResponse.success(result.getContent(), pagination);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<PracticeSessionDetailResponse> getSessionDetail(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        long userId = currentUserResolver.resolveUserId(authentication);
        return ApiResponse.success(sessionService.getSessionDetail(userId, sessionId));
    }
}
