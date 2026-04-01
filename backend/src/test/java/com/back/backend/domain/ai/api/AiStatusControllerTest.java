package com.back.backend.domain.ai.api;

import com.back.backend.domain.ai.usage.AiStatusService;
import com.back.backend.domain.ai.usage.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AiStatusController 단위 테스트
 * - Spring 컨텍스트 없이 standaloneSetup으로 컨트롤러 로직만 검증
 * - 응답 포맷 및 percentage 필드 검증
 * - permitAll() 등 보안 규칙 검증은 CookieJwtSecurityTest에서 담당
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GET /api/v1/ai/status")
class AiStatusControllerTest {

    @Mock
    private AiStatusService aiStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AiStatusController(aiStatusService))
            .build();
    }

    @Test
    @DisplayName("available=true: 200 응답")
    void getStatus_available_returns200() throws Exception {
        when(aiStatusService.getStatus()).thenReturn(
            new AiStatusResponse(true, null, null, List.of())
        );

        mockMvc.perform(get("/api/v1/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("available=true: providers 상세 및 percentage 포함")
    void getStatus_available_returnsProviderDetail() throws Exception {
        ProviderStatus gemini = ProviderStatus.of("gemini", ProviderAvailability.AVAILABLE,
            new MinuteUsage(3, 10, 30, 42),
            new DailyUsage(50, 250, 20, "2026-04-02T00:00:00Z"),
            new TokenUsageStat(50000L, 250000L, 20, 100000L, null, null)
        );

        when(aiStatusService.getStatus()).thenReturn(
            new AiStatusResponse(true, null, null, List.of(gemini))
        );

        mockMvc.perform(get("/api/v1/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.estimatedWaitSeconds").doesNotExist())
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.providers[0].name").value("gemini"))
            .andExpect(jsonPath("$.providers[0].status").value("available"))
            .andExpect(jsonPath("$.providers[0].minuteUsage.used").value(3))
            .andExpect(jsonPath("$.providers[0].minuteUsage.percentage").value(30))
            .andExpect(jsonPath("$.providers[0].dailyUsage.percentage").value(20))
            .andExpect(jsonPath("$.providers[0].tokenUsage.minutePercentage").value(20))
            .andExpect(jsonPath("$.providers[0].tokenUsage.dailyLimit").doesNotExist());
    }

    @Test
    @DisplayName("available=false (분당 초과): estimatedWaitSeconds, message 포함")
    void getStatus_minuteRateLimited_returnsWaitInfo() throws Exception {
        ProviderStatus limited = ProviderStatus.of("gemini", ProviderAvailability.MINUTE_RATE_LIMITED,
            new MinuteUsage(10, 10, 100, 37),
            new DailyUsage(50, 250, 20, "2026-04-02T00:00:00Z"),
            new TokenUsageStat(250000L, 250000L, 100, 100000L, null, null)
        );

        when(aiStatusService.getStatus()).thenReturn(
            new AiStatusResponse(false, 37,
                "AI 서비스가 잠시 과부하 상태입니다. 약 37초 후 다시 시도해주세요.",
                List.of(limited))
        );

        mockMvc.perform(get("/api/v1/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.estimatedWaitSeconds").value(37))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("37초")))
            .andExpect(jsonPath("$.providers[0].status").value("minute_rate_limited"))
            .andExpect(jsonPath("$.providers[0].minuteUsage.percentage").value(100));
    }

    @Test
    @DisplayName("available=false (일간 소진): daily_exhausted status")
    void getStatus_dailyExhausted_returnsDailyExhaustedStatus() throws Exception {
        ProviderStatus exhausted = ProviderStatus.of("gemini", ProviderAvailability.DAILY_EXHAUSTED,
            new MinuteUsage(0, 10, 0, 0),
            new DailyUsage(250, 250, 100, "2026-04-02T00:00:00Z"),
            new TokenUsageStat(0L, 250000L, 0, 0L, null, null)
        );

        when(aiStatusService.getStatus()).thenReturn(
            new AiStatusResponse(false, 32400,
                "오늘의 AI 서비스 사용량이 모두 소진되었습니다. 내일 오전 9시 이후 다시 시도해주세요.",
                List.of(exhausted))
        );

        mockMvc.perform(get("/api/v1/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.providers[0].status").value("daily_exhausted"))
            .andExpect(jsonPath("$.providers[0].dailyUsage.percentage").value(100));
    }
}
