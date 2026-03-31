package com.back.backend.domain.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiClientRouterTest {

    @Test
    @DisplayName("기본 provider의 AiClient를 반환한다")
    void getDefault_success() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI, null);

        AiClient result = router.getDefault();

        assertThat(result.getProvider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("특정 provider의 AiClient를 반환한다")
    void getClient_specificProvider() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClient openAiClient = mockClient(AiProvider.OPENAI);
        AiClientRouter router = new AiClientRouter(
            List.of(geminiClient, openAiClient), AiProvider.GEMINI, null
        );

        AiClient result = router.getClient(AiProvider.OPENAI);

        assertThat(result.getProvider()).isEqualTo(AiProvider.OPENAI);
    }

    @Test
    @DisplayName("등록되지 않은 provider 요청 시 예외를 던진다")
    void getClient_unknownProvider() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI, null);

        assertThatThrownBy(() -> router.getClient(AiProvider.CLAUDE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("등록되지 않은 AI provider");
    }

    // ─ fallback 관련 테스트 ─

    @Test
    @DisplayName("fallback provider가 설정되면 getFallback()이 해당 클라이언트를 반환한다")
    void getFallback_configured() {
        // given — Gemini 기본, Groq fallback
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClient groqClient = mockClient(AiProvider.GROQ);
        AiClientRouter router = new AiClientRouter(
            List.of(geminiClient, groqClient), AiProvider.GEMINI, AiProvider.GROQ
        );

        Optional<AiClient> fallback = router.getFallback();

        assertThat(fallback).isPresent();
        assertThat(fallback.get().getProvider()).isEqualTo(AiProvider.GROQ);
    }

    @Test
    @DisplayName("fallback provider가 null이면 getFallback()이 빈 Optional을 반환한다")
    void getFallback_notConfigured() {
        // fallback 미설정
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI, null);

        Optional<AiClient> fallback = router.getFallback();

        assertThat(fallback).isEmpty();
    }

    private AiClient mockClient(AiProvider provider) {
        AiClient client = mock(AiClient.class);
        when(client.getProvider()).thenReturn(provider);
        return client;
    }
}
