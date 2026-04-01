package com.back.backend.domain.ai.api;

import com.back.backend.domain.ai.usage.AiStatusService;
import com.back.backend.domain.ai.usage.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AiStatusController 테스트
 * - 인증 없이 접근 가능 확인
 * - 정상/비가용 상태 응답 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 상태 조회 컨트롤러")
class AiStatusControllerTest {

    @Mock
    private AiStatusService aiStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiStatusController controller = new AiStatusController(aiStatusService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/ai/status - 정상 응답")
    void testGetStatusAvailable() throws Exception {
        // given
        MinuteUsage geminiMinute = new MinuteUsage(3, 10, 30, 42);
        DailyUsage geminiDaily = new DailyUsage(50, 250, 20, "2026-04-02T00:00:00Z");
        TokenUsageStat geminiToken = new TokenUsageStat(50000L, 250000L, 20, 100000L, null, null);
        ProviderStatus gemini = ProviderStatus.of("gemini",
            ProviderAvailability.AVAILABLE,
            geminiMinute, geminiDaily, geminiToken);

        MinuteUsage groqMinute = new MinuteUsage(10, 30, 33, 58);
        DailyUsage groqDaily = new DailyUsage(120, 1000, 12, "2026-04-02T00:00:00Z");
        TokenUsageStat groqToken = new TokenUsageStat(3000L, 12000L, 25, 30000L, 100000L, 30);
        ProviderStatus groq = ProviderStatus.of("groq",
            ProviderAvailability.AVAILABLE,
            groqMinute, groqDaily, groqToken);

        AiStatusResponse response = new AiStatusResponse(
            true,  // available
            null,  // estimatedWaitSeconds
            null,  // message
            List.of(gemini, groq)
        );

        when(aiStatusService.getStatus()).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/ai/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.estimatedWaitSeconds").isEmpty())
            .andExpect(jsonPath("$.message").isEmpty())
            .andExpect(jsonPath("$.providers").isArray())
            .andExpect(jsonPath("$.providers[0].name").value("gemini"))
            .andExpect(jsonPath("$.providers[0].status").value("available"))
            .andExpect(jsonPath("$.providers[0].minuteUsage.used").value(3))
            .andExpect(jsonPath("$.providers[0].minuteUsage.limit").value(10))
            .andExpect(jsonPath("$.providers[0].minuteUsage.percentage").value(30))
            .andExpect(jsonPath("$.providers[0].dailyUsage.percentage").value(20))
            .andExpect(jsonPath("$.providers[0].tokenUsage.minutePercentage").value(20))
            .andExpect(jsonPath("$.providers[1].name").value("groq"))
            .andExpect(jsonPath("$.providers[1].tokenUsage.dailyPercentage").value(30));

        verify(aiStatusService, times(1)).getStatus();
    }

    @Test
    @DisplayName("GET /api/v1/ai/status - 분당 한도 초과")
    void testGetStatusMinuteRateLimited() throws Exception {
        // given
        MinuteUsage geminiMinute = new MinuteUsage(10, 10, 100, 37);
        DailyUsage geminiDaily = new DailyUsage(50, 250, 20, "2026-04-02T00:00:00Z");
        TokenUsageStat geminiToken = new TokenUsageStat(250000L, 250000L, 100, 100000L, null, null);
        ProviderStatus gemini = ProviderStatus.of("gemini",
            ProviderAvailability.MINUTE_RATE_LIMITED,
            geminiMinute, geminiDaily, geminiToken);

        AiStatusResponse response = new AiStatusResponse(
            false,
            37,
            "AI 서비스가 잠시 과부하 상태입니다. 약 37초 후 다시 시도해주세요.",
            List.of(gemini)
        );

        when(aiStatusService.getStatus()).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/ai/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.estimatedWaitSeconds").value(37))
            .andExpect(jsonPath("$.message").containsString("과부하"))
            .andExpect(jsonPath("$.providers[0].status").value("minute_rate_limited"))
            .andExpect(jsonPath("$.providers[0].minuteUsage.percentage").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/ai/status - 일간 한도 소진")
    void testGetStatusDailyExhausted() throws Exception {
        // given
        MinuteUsage geminiMinute = new MinuteUsage(0, 10, 0, 0);
        DailyUsage geminiDaily = new DailyUsage(250, 250, 100, "2026-04-02T00:00:00Z");
        TokenUsageStat geminiToken = new TokenUsageStat(0L, 250000L, 0, 0L, null, null);
        ProviderStatus gemini = ProviderStatus.of("gemini",
            ProviderAvailability.DAILY_EXHAUSTED,
            geminiMinute, geminiDaily, geminiToken);

        AiStatusResponse response = new AiStatusResponse(
            false,
            32400,  // 9시간 (UTC 자정까지)
            "오늘의 AI 서비스 사용량이 모두 소진되었습니다. 내일 오전 9시 이후 다시 시도해주세요.",
            List.of(gemini)
        );

        when(aiStatusService.getStatus()).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/ai/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.estimatedWaitSeconds").value(32400))
            .andExpect(jsonPath("$.message").containsString("소진"))
            .andExpect(jsonPath("$.providers[0].status").value("daily_exhausted"))
            .andExpect(jsonPath("$.providers[0].dailyUsage.percentage").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/ai/status - 인증 불필요")
    void testGetStatusNoAuthRequired() throws Exception {
        // given
        AiStatusResponse response = new AiStatusResponse(true, null, null, List.of());
        when(aiStatusService.getStatus()).thenReturn(response);

        // when & then
        // Authorization 헤더 없이 호출하면 200 OK 반환
        mockMvc.perform(get("/api/v1/ai/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(aiStatusService, times(1)).getStatus();
    }
}
