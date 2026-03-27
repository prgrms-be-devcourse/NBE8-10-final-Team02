package com.back.backend.domain.ai.client.gemini;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiRequest;
import com.back.backend.domain.ai.client.AiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

class GeminiClientTest {

    private MockWebServer mockWebServer;
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        GeminiClientProperties properties = new GeminiClientProperties(
            "test-api-key",
            mockWebServer.url("/v1beta").toString(),
            "gemini-2.5-flash",
            new GeminiClientProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(5)),
            new GeminiClientProperties.Retry(0)
        );

        RestClient restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(new SimpleClientHttpRequestFactory())  // Apache HttpClient 5 자동선택 방지
            .build();

        geminiClient = new GeminiClient(restClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("정상 응답 시 content와 tokenUsage를 반환한다")
    void call_success() {
        // given
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

        // when
        AiResponse response = geminiClient.call(request);

        // then
        assertThat(response.content()).isEqualTo("{\"key\":\"value\"}");
        assertThat(response.tokenUsage().promptTokens()).isEqualTo(10);
        assertThat(response.tokenUsage().completionTokens()).isEqualTo(20);
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(30);
    }

    @Test
    @DisplayName("빈 candidates 응답 시 AiClientException을 던진다")
    void call_emptyCandidates() {
        // given
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(emptyCandidatesResponseBody()));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        // when & then
        assertThatThrownBy(() -> geminiClient.call(request))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("빈 응답");
    }

    @Test
    @DisplayName("API 에러 응답(4xx/5xx) 시 AiClientException을 던진다")
    void call_apiError() {
        // given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":{\"message\":\"Rate limit exceeded\"}}"));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        // when & then
        assertThatThrownBy(() -> geminiClient.call(request))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("API 에러");
    }

    @Test
    @DisplayName("usageMetadata가 없어도 정상 응답을 반환한다")
    void call_nullUsageMetadata() {
        // given
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(noUsageMetadataResponseBody()));

        AiRequest request = new AiRequest("system", "developer", "user", 0.5, 1000);

        // when
        AiResponse response = geminiClient.call(request);

        // then
        assertThat(response.content()).isEqualTo("{\"key\":\"value\"}");
        assertThat(response.tokenUsage().totalTokens()).isZero();
    }

    // --- test fixtures ---

    private String successResponseBody() {
        return """
                  {
                    "candidates": [{
                      "content": {
                        "parts": [{"text": "{\\"key\\":\\"value\\"}"}],
                        "role": "model"
                      }
                    }],
                    "usageMetadata": {
                      "promptTokenCount": 10,
                      "candidatesTokenCount": 20,
                      "totalTokenCount": 30
                    }
                  }
                  """;
    }

    private String emptyCandidatesResponseBody() {
        return """
                  {
                    "candidates": [],
                    "usageMetadata": {
                      "promptTokenCount": 10,
                      "candidatesTokenCount": 0,
                      "totalTokenCount": 10
                    }
                  }
                  """;
    }

    private String noUsageMetadataResponseBody() {
        return """
                  {
                    "candidates": [{
                      "content": {
                        "parts": [{"text": "{\\"key\\":\\"value\\"}"}],
                        "role": "model"
                      }
                    }]
                  }
                  """;
    }
}
