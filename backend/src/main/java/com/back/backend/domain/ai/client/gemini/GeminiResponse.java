package com.back.backend.domain.ai.client.gemini;

import com.back.backend.domain.ai.client.AiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Gemini API 응답 DTO
 * generateContent 응답에서 생성 텍스트와 토큰 사용량을 추출
 */
public record GeminiResponse(
    List<Candidate> candidates,
    @JsonProperty("usageMetadata") UsageMetadata usageMetadata
) {
    public record Candidate(
        Content content
    ) {
    }

    public record Content(
        List<Part> parts,
        String role
    ) {
    }

    public record Part(
        String text
    ) {
    }

    public record UsageMetadata(
        @JsonProperty("promptTokenCount") int promptTokenCount,
        @JsonProperty("candidatesTokenCount") int candidatesTokenCount,
        @JsonProperty("totalTokenCount") int totalTokenCount
    ) {
    }

    /**
     * 첫 번째 candidate의 텍스트를 추출
     * 유효한 응답이 없으면 빈 Optional을 반환
     */
    public Optional<String> extractText() {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Content content = candidates.getFirst().content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(content.parts().getFirst().text());
    }

    /**
     * Gemini 토큰 사용량 → 공통 TokenUsage로 변환한다.
     * usageMetadata가 없으면 0으로 채운다 — 토큰 집계 누락은 허용하되 요청 실패는 방지
     */
    public AiResponse.TokenUsage toTokenUsage() {
        if (usageMetadata == null) {
            return new AiResponse.TokenUsage(0, 0, 0);
        }
        return new AiResponse.TokenUsage(
            usageMetadata.promptTokenCount(),
            usageMetadata.candidatesTokenCount(),
            usageMetadata.totalTokenCount()
        );
    }
}
