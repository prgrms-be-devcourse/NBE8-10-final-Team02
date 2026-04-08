package com.back.backend.domain.ai.client.vertexai;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.FileInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vertex AI 실제 연동 테스트
 * GCP 인증 → Access Token 발급 → Vertex AI generateContent 호출까지 검증
 * CI 환경에서는 실행하지 않음 (@Tag("manual"))
 */
@Tag("manual")
class VertexAiConnectionTest {

    private static final String PROJECT_ID = "project-0642120c-b67a-4a76-a64";
    private static final String LOCATION = "us-central1";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String CREDENTIALS_PATH = "vertex-ai-key.json";

    @Test
    @DisplayName("GCP Service Account에서 Access Token을 발급받을 수 있다")
    void getAccessToken() throws Exception {
        try (var stream = new FileInputStream(CREDENTIALS_PATH)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();

            assertThat(token).isNotBlank();
            System.out.println("✓ Access Token 발급 성공 (길이: " + token.length() + ")");
        }
    }

    @Test
    @DisplayName("Vertex AI generateContent API를 호출할 수 있다")
    void callVertexAi() throws Exception {
        // 1. Access Token 발급
        GoogleCredentials credentials;
        try (var stream = new FileInputStream(CREDENTIALS_PATH)) {
            credentials = GoogleCredentials.fromStream(stream)
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        }
        credentials.refreshIfExpired();
        String token = credentials.getAccessToken().getTokenValue();

        // 2. Vertex AI 엔드포인트 구성
        String baseUrl = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s",
            LOCATION, PROJECT_ID, LOCATION, MODEL
        );

        // 3. 간단한 요청 전송
        String requestBody = """
            {
              "contents": [{
                "role": "user",
                "parts": [{"text": "1+1은? 숫자만 답해줘."}]
              }],
              "generationConfig": {
                "temperature": 0.0,
                "maxOutputTokens": 10
              }
            }
            """;

        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();

        String response = restClient.post()
            .uri(":generateContent")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);

        assertThat(response).isNotNull();
        assertThat(response).contains("candidates");
        System.out.println("✓ Vertex AI 호출 성공!");
        System.out.println("응답: " + response);
    }
}
