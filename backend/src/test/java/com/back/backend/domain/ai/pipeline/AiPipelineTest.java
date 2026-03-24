package com.back.backend.domain.ai.pipeline;

import com.back.backend.domain.ai.client.*;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiPipelineTest {

    private AiClientRouter router;
    private AiClient mockAiClient;
    private AiResponseValidator mockValidator;
    private ValidationRegistry validationRegistry;
    private AiPipeline pipeline;

    @BeforeEach
    void setUp() {
        router = mock(AiClientRouter.class);
        mockAiClient = mock(AiClient.class);
        mockValidator = mock(AiResponseValidator.class);
        validationRegistry = mock(ValidationRegistry.class);

        when(router.getDefault()).thenReturn(mockAiClient);
        when(validationRegistry.get(anyString())).thenReturn(mockValidator);

        pipeline = new AiPipeline(
            router,
            PromptTemplateRegistry.createDefault(),
            validationRegistry,
            new PromptLoader(),
            new JsonSchemaValidator(new ObjectMapper())
        );
    }

    // 성공 흐름

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

    // 재시도/실패 케이스

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

    @Test
    @DisplayName("AiClientException은 재시도 없이 즉시 전파된다")
    void execute_aiClientException_propagatesImmediately() {
        when(mockAiClient.call(any())).thenThrow(
            new AiClientException(AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE, "AI 호출 실패")
        );

        assertThatThrownBy(() -> pipeline.execute("ai.portfolio.summary.v1", "{}"))
            .isInstanceOf(AiClientException.class);

        verify(mockAiClient, times(1)).call(any());
    }

    private AiResponse response(String content) {
        return new AiResponse(content, new AiResponse.TokenUsage(100, 200, 300));
    }
}
