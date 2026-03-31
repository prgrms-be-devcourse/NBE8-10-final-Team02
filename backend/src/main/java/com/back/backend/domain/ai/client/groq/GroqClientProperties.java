package com.back.backend.domain.ai.client.groq;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Groq API 연동 설정
 * application.yml의 ai.groq 속성과 바인딩
 * Groq는 OpenAI 호환 API를 제공하므로 base-url이 OpenAI 형식
 */
@ConfigurationProperties(prefix = "ai.groq")
public record GroqClientProperties(
    String apiKey,    // Groq API 키
    String baseUrl,   // https://api.groq.com/openai/v1
    String model,     // llama-3.3-70b-versatile
    Timeout timeout,
    Retry retry
) {
    public record Timeout(
        Duration connect,  // 연결 타임아웃
        Duration read      // 응답 읽기 타임아웃
    ) {
    }

    public record Retry(
        int maxAttempts  // 최대 재시도 횟수
    ) {
    }
}
