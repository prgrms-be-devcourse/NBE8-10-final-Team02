package com.back.backend.domain.ai.client.vertexai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Vertex AI 연동 설정
 * application.yml의 ai.vertex-ai 속성과 바인딩
 * GCP Service Account 인증 기반 — API 키 대신 credentialsPath로 인증
 */
@ConfigurationProperties(prefix = "ai.vertex-ai")
public record VertexAiClientProperties(
    String projectId,       // GCP 프로젝트 ID
    String location,        // 리전 (예: us-central1)
    String model,           // 모델명 (예: gemini-2.5-flash)
    String credentialsPath, // Service Account JSON 키 파일 경로
    Timeout timeout,
    Retry retry
) {
    public record Timeout(
        Duration connect,
        Duration read
    ) {
    }

    public record Retry(
        int maxAttempts
    ) {
    }

    /**
     * Vertex AI generateContent 엔드포인트 base URL을 조립
     * 형식: https://{location}-aiplatform.googleapis.com/v1/projects/{projectId}/locations/{location}/publishers/google/models/{model}
     */
    public String buildEndpointUrl() {
        return String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s",
            location, projectId, location, model
        );
    }
}
