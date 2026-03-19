package com.back.backend.ai.client.gemini;

import com.back.backend.ai.client.AiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
     * Gemini 응답 → 공통 AiResponse로 변환
     */
    public AiResponse toAiResponse() {
        String content = candidates.getFirst().content().parts().getFirst().text();

        AiResponse.TokenUsage tokenUsage = new AiResponse.TokenUsage(
            usageMetadata.promptTokenCount(),
            usageMetadata.candidatesTokenCount(),
            usageMetadata.totalTokenCount()
        );

        return new AiResponse(content, tokenUsage);
    }
}
