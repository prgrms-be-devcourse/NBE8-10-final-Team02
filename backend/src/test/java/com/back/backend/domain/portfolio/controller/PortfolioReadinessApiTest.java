package com.back.backend.domain.portfolio.controller;

import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse;
import com.back.backend.domain.portfolio.service.PortfolioReadinessService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioReadinessApiTest extends ApiTestBase {

    @MockitoBean
    private PortfolioReadinessService portfolioReadinessService;

    @Test
    void getReadiness_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/me/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void getReadiness_returns200WithDashboardData() throws Exception {
        PortfolioReadinessResponse response = new PortfolioReadinessResponse(
                new PortfolioReadinessResponse.Profile(
                        1L,
                        "tester",
                        "tester@example.com",
                        "https://example.com/avatar.png"
                ),
                new PortfolioReadinessResponse.Github(
                        "connected",
                        "public_only",
                        2,
                        PortfolioReadinessResponse.CountMetric.notReady()
                ),
                new PortfolioReadinessResponse.Documents(3, 2, 1),
                new PortfolioReadinessResponse.Readiness(List.of(), "start_application", true),
                new PortfolioReadinessResponse.Alerts(
                        PortfolioReadinessResponse.RecentFailedJobs.notReady()
                )
        );
        given(portfolioReadinessService.getReadiness(1L)).willReturn(response);

        mockMvc.perform(get("/api/v1/portfolios/me/readiness").with(authenticated(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profile.userId").value(1))
                .andExpect(jsonPath("$.data.github.connectionStatus").value("connected"))
                .andExpect(jsonPath("$.data.github.recentCollectedCommitCount.status").value("not_ready"))
                .andExpect(jsonPath("$.data.github.recentCollectedCommitCount.value").value(nullValue()))
                .andExpect(jsonPath("$.data.documents.extractFailedCount").value(1))
                .andExpect(jsonPath("$.data.readiness.nextRecommendedAction").value("start_application"))
                .andExpect(jsonPath("$.data.alerts.recentFailedJobs.status").value("not_ready"))
                .andExpect(jsonPath("$.data.alerts.recentFailedJobs.items").value(nullValue()));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}
