package com.back.backend.domain.application.controller;

import com.back.backend.domain.application.dto.request.CreateApplicationRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationQuestionsRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationSourcesRequest;
import com.back.backend.domain.application.dto.request.UpdateApplicationRequest;
import com.back.backend.domain.application.dto.response.ApplicationQuestionResponse;
import com.back.backend.domain.application.dto.response.ApplicationResponse;
import com.back.backend.domain.application.dto.response.ApplicationSourceBindingResponse;
import com.back.backend.domain.application.service.ApplicationService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicationResponse> createApplication(
            Authentication authentication,
            @RequestBody CreateApplicationRequest request
    ) {
        return ApiResponse.success(
                applicationService.createApplication(currentUserResolver.resolveUserId(authentication), request)
        );
    }

    @GetMapping
    public ApiResponse<List<ApplicationResponse>> getApplications(Authentication authentication) {
        return ApiResponse.success(
                applicationService.getApplications(currentUserResolver.resolveUserId(authentication))
        );
    }

    @GetMapping("/{applicationId}")
    public ApiResponse<ApplicationResponse> getApplication(
            Authentication authentication,
            @PathVariable long applicationId
    ) {
        return ApiResponse.success(
                applicationService.getApplication(currentUserResolver.resolveUserId(authentication), applicationId)
        );
    }

    @PatchMapping("/{applicationId}")
    public ApiResponse<ApplicationResponse> updateApplication(
            Authentication authentication,
            @PathVariable long applicationId,
            @RequestBody UpdateApplicationRequest request
    ) {
        return ApiResponse.success(
                applicationService.updateApplication(
                        currentUserResolver.resolveUserId(authentication),
                        applicationId,
                        request
                )
        );
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> deleteApplication(
            Authentication authentication,
            @PathVariable long applicationId
    ) {
        applicationService.deleteApplication(currentUserResolver.resolveUserId(authentication), applicationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{applicationId}/sources")
    public ApiResponse<ApplicationSourceBindingResponse> getSources(
            Authentication authentication,
            @PathVariable long applicationId
    ) {
        return ApiResponse.success(
                applicationService.getSources(currentUserResolver.resolveUserId(authentication), applicationId)
        );
    }

    @PutMapping("/{applicationId}/sources")
    public ApiResponse<ApplicationSourceBindingResponse> saveSources(
            Authentication authentication,
            @PathVariable long applicationId,
            @RequestBody SaveApplicationSourcesRequest request
    ) {
        return ApiResponse.success(
                applicationService.saveSources(
                        currentUserResolver.resolveUserId(authentication),
                        applicationId,
                        request
                )
        );
    }

    @PostMapping("/{applicationId}/questions")
    public ApiResponse<List<ApplicationQuestionResponse>> saveQuestions(
            Authentication authentication,
            @PathVariable long applicationId,
            @RequestBody SaveApplicationQuestionsRequest request
    ) {
        return ApiResponse.success(
                applicationService.saveQuestions(
                        currentUserResolver.resolveUserId(authentication),
                        applicationId,
                        request
                )
        );
    }

    @GetMapping("/{applicationId}/questions")
    public ApiResponse<List<ApplicationQuestionResponse>> getQuestions(
            Authentication authentication,
            @PathVariable long applicationId
    ) {
        return ApiResponse.success(
                applicationService.getQuestions(currentUserResolver.resolveUserId(authentication), applicationId)
        );
    }
}
