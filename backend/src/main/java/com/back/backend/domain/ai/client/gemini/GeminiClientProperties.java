package com.back.backend.domain.ai.client.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gemini API 연동 설정
 * application.yml의 ai.gemini 속성과 바인딩
 * 새 AI 모델 추가 시 이 클래스를 복제하면 됨
 */
@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiClientProperties(
    String apiKey,
    String baseUrl,
    String model,
    Timeout timeout,
    Retry retry
) {
    public record Timeout(
        Duration connect,  // 연결 타임아웃
        Duration read// 응답 읽기 타임아웃 — AI 생성은 오래 걸릴 수 있으므로 넉넉히
    ) {
    }

    public record Retry(
        int maxAttempts  // 최대 재시도 횟수 0이면 재시도 없음
    ) {
    }
}
