package com.back.backend.domain.ai.client;

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
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI);

        AiClient result = router.getDefault();

        assertThat(result.getProvider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    @DisplayName("특정 provider의 AiClient를 반환한다")
    void getClient_specificProvider() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClient vertexClient = mockClient(AiProvider.VERTEX_AI);
        AiClientRouter router = new AiClientRouter(
            List.of(geminiClient, vertexClient), AiProvider.GEMINI
        );

        AiClient result = router.getClient(AiProvider.VERTEX_AI);

        assertThat(result.getProvider()).isEqualTo(AiProvider.VERTEX_AI);
    }

    @Test
    @DisplayName("등록되지 않은 provider 요청 시 예외를 던진다")
    void getClient_unknownProvider() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI);

        assertThatThrownBy(() -> router.getClient(AiProvider.CLAUDE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("등록되지 않은 AI provider");
    }

    @Test
    @DisplayName("getAllClients()는 등록된 모든 클라이언트를 반환한다")
    void getAllClients_returnsAll() {
        AiClient geminiClient = mockClient(AiProvider.GEMINI);
        AiClient groqClient = mockClient(AiProvider.GROQ);
        AiClientRouter router = new AiClientRouter(
            List.of(geminiClient, groqClient), AiProvider.GEMINI
        );

        assertThat(router.getAllClients()).hasSize(2);
    }

    private AiClient mockClient(AiProvider provider) {
        AiClient client = mock(AiClient.class);
        when(client.getProvider()).thenReturn(provider);
        return client;
    }
}
