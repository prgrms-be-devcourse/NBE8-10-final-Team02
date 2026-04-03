package com.back.backend.domain.activity.controller;

import com.back.backend.domain.activity.dto.ActivityEntryDto;
import com.back.backend.domain.activity.dto.ActivityStatsResponse;
import com.back.backend.domain.activity.dto.StreakResponse;
import com.back.backend.domain.activity.service.ActivityQueryService;
import com.back.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    @GetMapping("/streak")
    public ApiResponse<StreakResponse> getStreak(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(activityQueryService.getStreak(userId));
    }

    @GetMapping("/heatmap")
    public ApiResponse<List<ActivityEntryDto>> getHeatmap(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "180") int days
    ) {
        if (days < 1 || days > 365) {
            days = 180;
        }
        return ApiResponse.success(activityQueryService.getHeatmap(userId, days));
    }

    @GetMapping("/stats")
    public ApiResponse<ActivityStatsResponse> getStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(activityQueryService.getStats(userId));
    }
}
