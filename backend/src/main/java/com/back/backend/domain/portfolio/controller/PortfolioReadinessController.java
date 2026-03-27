package com.back.backend.domain.portfolio.controller;

import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse;
import com.back.backend.domain.portfolio.service.PortfolioReadinessService;
import com.back.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portfolios")
public class PortfolioReadinessController {

    private final PortfolioReadinessService portfolioReadinessService;

    @GetMapping("/me/readiness")
    public ApiResponse<PortfolioReadinessResponse> getReadiness(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(portfolioReadinessService.getReadiness(userId));
    }
}
