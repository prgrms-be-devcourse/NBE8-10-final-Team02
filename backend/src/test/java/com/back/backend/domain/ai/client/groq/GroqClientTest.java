package com.back.backend.domain.ai.client.groq;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiRequest;
import com.back.backend.domain.ai.client.AiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GroqClient 단위 테스트
 * MockWebServer로 Groq(OpenAI 호환) API 응답을 시뮬레이션하여 요청 변환, 응답 파싱, 에러 처리를 검증
 */
class GroqClientTest {

    private MockWebServer mockWebServer;
    private GroqClient groqClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // MockWebServer URL을 baseUrl로 사용
        GroqClientProperties properties = new GroqClientProperties(
            "test-groq-api-key",
            mockWebServer.url("/openai/v1").toString(),
            "llama-3.3-70b-versatile",
            new GroqClientProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(5)),
            new GroqClientProperties.Retry(0)
        );

        RestClient restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

        groqClient = new GroqClient(restClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("정상 응답 시 content와 tokenUsage를 반환한다")
    void call_success() {
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(successResponseBody()));

        AiRequest request = new AiRequest(
            "system prompt",
            "developer prompt",
            "user message",
            0.5,
            1000
        );

        AiResponse response = groqClient.call(request);

        // 응답 텍스트와 토큰 사용량 검증
        assertThat(response.content()).isEqualTo("{\"key\":\"value\"}");
        assertThat(response.tokenUsage().promptTokens()).isEqualTo(50);
        assertThat(response.tokenUsage().completionTokens()).isEqualTo(30);
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(80);
    }

    @Test
    @DisplayName("요청 시 Authorization 헤더와 올바른 엔드포인트를 사용한다")
    void call_requestFormat() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(successResponseBody()));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        groqClient.call(request);

        // 실제 전송된 HTTP 요청 검증
        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/openai/v1/chat/completions");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-groq-api-key");
        assertThat(recorded.getBody().readUtf8()).contains("llama-3.3-70b-versatile");
    }

    @Test
    @DisplayName("빈 choices 응답 시 AiClientException을 던진다")
    void call_emptyChoices() {
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(emptyChoicesResponseBody()));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        assertThatThrownBy(() -> groqClient.call(request))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("빈 응답");
    }

    @Test
    @DisplayName("429 에러 시 할당량 초과 AiClientException을 던진다")
    void call_rateLimitError() {
        // Groq 무료 티어 한도 초과 시뮬레이션
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":{\"message\":\"Rate limit reached\"}}"));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        assertThatThrownBy(() -> groqClient.call(request))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("호출 횟수");
    }

    @Test
    @DisplayName("500 서버 에러 시 AiClientException을 던진다")
    void call_serverError() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":{\"message\":\"Internal server error\"}}"));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        assertThatThrownBy(() -> groqClient.call(request))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("Groq API 에러");
    }

    @Test
    @DisplayName("usage가 없어도 정상 응답을 반환한다")
    void call_nullUsage() {
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(noUsageResponseBody()));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        AiResponse response = groqClient.call(request);

        // usage 없으면 토큰 0으로 채움
        assertThat(response.content()).isEqualTo("{\"key\":\"value\"}");
        assertThat(response.tokenUsage().totalTokens()).isZero();
    }

    // --- test fixtures (OpenAI Chat Completions 형식) ---

    private String successResponseBody() {
        return """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "{\\"key\\":\\"value\\"}"
                },
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 50,
                "completion_tokens": 30,
                "total_tokens": 80
              }
            }
            """;
    }

    private String emptyChoicesResponseBody() {
        return """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "choices": [],
              "usage": {
                "prompt_tokens": 50,
                "completion_tokens": 0,
                "total_tokens": 50
              }
            }
            """;
    }

    private String noUsageResponseBody() {
        return """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "{\\"key\\":\\"value\\"}"
                },
                "finish_reason": "stop"
              }]
            }
            """;
    }
}
