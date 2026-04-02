package com.back.backend.domain.followup.controller;

import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.service.FollowupRuleService;
import com.back.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/followup-rules", "/api/followup-rules"})
@RequiredArgsConstructor
public class FollowupRuleController {

    private final FollowupRuleService followupRuleService;

    @PostMapping("/analyze")
    public ApiResponse<FollowupAnalyzeResponse> analyze(@Valid @RequestBody FollowupAnalyzeRequest request) {
        return ApiResponse.success(followupRuleService.analyze(request));
    }
}
