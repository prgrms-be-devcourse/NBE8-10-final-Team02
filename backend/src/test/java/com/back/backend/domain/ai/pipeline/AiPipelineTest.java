package com.back.backend.domain.ai.pipeline;

import com.back.backend.domain.ai.client.*;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.usage.AiUsageRecorder;
import com.back.backend.domain.ai.validation.AiResponseValidator;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import com.back.backend.domain.ai.validation.ValidationResult;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiPipelineTest {

    private AiClientRouter router;
    private AiClient mockAiClient;
    private AiResponseValidator mockValidator;
    private ValidationRegistry validationRegistry;
    private AiUsageRecorder usageRecorder;
    private AiPipeline pipeline;


    /**
     * AiClient, ValidationRegistry는 mock으로 격리하여 AI 호출/검증 결과를 제어
     * PromptTemplateRegistry, PromptLoader, JsonSchemaValidator는 실제 인스턴스 사용
     * → 템플릿 조회, 프롬프트 로딩, JSON 파싱은 실제 동작을 검증
     */
    @BeforeEach
    void setUp() {
        router = mock(AiClientRouter.class);
        mockAiClient = mock(AiClient.class);
        mockValidator = mock(AiResponseValidator.class);
        validationRegistry = mock(ValidationRegistry.class);
        usageRecorder = mock(AiUsageRecorder.class);

        when(router.getDefault()).thenReturn(mockAiClient);
        when(router.getClient(any())).thenReturn(mockAiClient); // resolveClient()에서 preferredProvider로 조회 시
        when(validationRegistry.get(anyString())).thenReturn(mockValidator);

        // 테스트에서는 동시성 제한 없이 즉시 실행되는 Limiter 사용
        AiConcurrencyLimiter concurrencyLimiter = new AiConcurrencyLimiter(100, 60);

        pipeline = new AiPipeline(
            router,
            PromptTemplateRegistry.createDefault(),
            validationRegistry,
            new PromptLoader(),
            new JsonSchemaValidator(new ObjectMapper()),
            usageRecorder,
            concurrencyLimiter,
            new com.back.backend.domain.ai.recovery.TruncatedJsonArrayRecovery(new ObjectMapper())
        );
    }

    // ─ 성공 흐름 테스트 ─

    @Test
    @DisplayName("정상 응답이 오면 검증 통과 후 JsonNode를 반환한다")
    void execute_success() {
        when(mockAiClient.call(any())).thenReturn(response("{}"));
        when(mockValidator.validate(any())).thenReturn(ValidationResult.success());

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
        assertThat(result.isObject()).isTrue();
    }

    @Test
    @DisplayName("경고가 있어도 검증 통과 시 JsonNode를 반환한다")
    void execute_successWithWarnings() {
        when(mockAiClient.call(any())).thenReturn(response("{}"));
        when(mockValidator.validate(any()))
            .thenReturn(ValidationResult.successWithWarnings(List.of("경고 메시지")));

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
    }

    // ─ 재시도/실패 케이스 테스트 ─

    @Test
    @DisplayName("1회 파싱 실패 후 재시도 성공")
    void execute_retryOnParseFailure() {
        when(mockAiClient.call(any()))
            .thenReturn(response("invalid json {{{"))
            .thenReturn(response("{}"));
        when(mockValidator.validate(any())).thenReturn(ValidationResult.success());

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
        verify(mockAiClient, times(2)).call(any());
    }

    @Test
    @DisplayName("1회 검증 실패 후 재시도 성공")
    void execute_retryOnValidationFailure() {
        when(mockAiClient.call(any())).thenReturn(response("{}"));
        when(mockValidator.validate(any()))
            .thenReturn(ValidationResult.failure("검증 실패"))
            .thenReturn(ValidationResult.success());

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
        verify(mockAiClient, times(2)).call(any());
    }

    @Test
    @DisplayName("maxRetries 초과 시 ServiceException을 던진다")
    void execute_maxRetriesExceeded() {
        // portfolio_summary maxRetries=2 → 총 3회 시도
        when(mockAiClient.call(any())).thenReturn(response("invalid json"));

        assertThatThrownBy(() -> pipeline.execute("ai.portfolio.summary.v1", "{}"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("maxRetries 초과");

        verify(mockAiClient, times(3)).call(any());
    }

    // ─ fallback 전환 테스트 ─

    @Test
    @DisplayName("기본 provider rate limit 시 fallback provider로 전환하여 성공한다")
    void execute_fallbackOnAiClientException() {
        // 기본 provider(Gemini) rate limit 실패, fallback provider(Groq) 성공
        AiClient fallbackClient = mock(AiClient.class);
        when(fallbackClient.getProvider()).thenReturn(AiProvider.GROQ);
        when(fallbackClient.call(any())).thenReturn(response("{}"));

        when(router.getFallback()).thenReturn(Optional.of(fallbackClient));
        when(mockAiClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.BAD_GATEWAY, "Gemini 429 할당량 초과",
                RateLimitType.MINUTE, 60, null)
        );
        when(mockValidator.validate(any())).thenReturn(ValidationResult.success());

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
        verify(mockAiClient, times(1)).call(any());
        verify(fallbackClient, times(1)).call(any());
    }

    @Test
    @DisplayName("rate limit이 아닌 오류는 fallback 없이 즉시 전파된다")
    void execute_nonRateLimitError_noFallback() {
        AiClient fallbackClient = mock(AiClient.class);
        when(router.getFallback()).thenReturn(Optional.of(fallbackClient));
        when(mockAiClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE, "Gemini 네트워크 오류")
        );

        assertThatThrownBy(() -> pipeline.execute("ai.portfolio.summary.v1", "{}"))
            .isInstanceOf(AiClientException.class);

        verify(mockAiClient, times(1)).call(any());
        verify(fallbackClient, never()).call(any());
    }

    @Test
    @DisplayName("기본 provider rate limit 후 fallback provider도 실패하면 예외를 전파한다")
    void execute_fallbackAlsoFails() {
        AiClient fallbackClient = mock(AiClient.class);
        when(fallbackClient.getProvider()).thenReturn(AiProvider.GROQ);
        when(fallbackClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GROQ,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.BAD_GATEWAY, "Groq도 실패",
                RateLimitType.MINUTE, 60, null)
        );

        when(router.getFallback()).thenReturn(Optional.of(fallbackClient));
        when(mockAiClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.BAD_GATEWAY, "Gemini rate limit",
                RateLimitType.MINUTE, 60, null)
        );

        assertThatThrownBy(() -> pipeline.execute("ai.portfolio.summary.v1", "{}"))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("Groq");

        verify(mockAiClient, times(1)).call(any());
        verify(fallbackClient, times(1)).call(any());
    }

    @Test
    @DisplayName("fallback provider에서도 파싱/검증 재시도가 동작한다")
    void execute_fallbackRetryOnParseFailure() {
        // Gemini rate limit → Groq에서 1회 파싱 실패 후 2회째 성공
        AiClient fallbackClient = mock(AiClient.class);
        when(fallbackClient.getProvider()).thenReturn(AiProvider.GROQ);
        when(fallbackClient.call(any()))
            .thenReturn(response("invalid json"))
            .thenReturn(response("{}"));

        when(router.getFallback()).thenReturn(Optional.of(fallbackClient));
        when(mockAiClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.BAD_GATEWAY, "Gemini rate limit",
                RateLimitType.MINUTE, 60, null)
        );
        when(mockValidator.validate(any())).thenReturn(ValidationResult.success());

        JsonNode result = pipeline.execute("ai.portfolio.summary.v1", "{}");

        assertThat(result).isNotNull();
        verify(fallbackClient, times(2)).call(any());
    }

    /**
     * 테스트용 AiResponse 생성 헬퍼 — TokenUsage는 고정값 사용
     */
    private AiResponse response(String content) {
        return new AiResponse(content, new AiResponse.TokenUsage(100, 200, 300));
    }
}
