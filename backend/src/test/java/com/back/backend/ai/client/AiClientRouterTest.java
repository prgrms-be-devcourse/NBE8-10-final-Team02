package com.back.backend.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiClientRouterTest {

    @Test
    @DisplayName("기본 provider의 AiClient를 반환한다")
    void getDefault_success() {
        // given
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI);

        // when
        AiClient result = router.getDefault();

        // then
        assertThat(result.getProvider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("특정 provider의 AiClient를 반환한다")
    void getClient_specificProvider() {
        // given
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClient openAiClient = mockClient(AiProvider.OPENAI);
        AiClientRouter router = new AiClientRouter(
            List.of(geminiClient, openAiClient), AiProvider.GEMINI
        );

        // when
        AiClient result = router.getClient(AiProvider.OPENAI);

        // then
        assertThat(result.getProvider()).isEqualTo(AiProvider.OPENAI);
    }

    @Test
    @DisplayName("등록되지 않은 provider 요청 시 예외를 던진다")
    void getClient_unknownProvider() {
        // given
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI);

        // when & then
        assertThatThrownBy(() -> router.getClient(AiProvider.CLAUDE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("등록되지 않은 AI provider");
    }

    private AiClient mockClient(AiProvider provider) {
        AiClient client = mock(AiClient.class);
        when(client.getProvider()).thenReturn(provider);
        return client;
    }
}
