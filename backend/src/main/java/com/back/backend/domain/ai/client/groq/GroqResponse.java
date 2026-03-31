package com.back.backend.domain.ai.client.groq;

import com.back.backend.domain.ai.client.AiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Groq API 응답 DTO (OpenAI Chat Completions 호환 형식)
 * choices[0].message.content에서 생성된 텍스트를 추출
 * usage에서 토큰 사용량을 추출
 */
public record GroqResponse(
    List<Choice> choices,
    Usage usage
) {
    public record Choice(
        Message message,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Message(
        String role,
        String content
    ) {
    }

    public record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {
    }

    /**
     * 첫 번째 choice의 메시지 content를 추출
     * 유효한 응답이 없으면 빈 Optional 반환
     */
    public Optional<String> extractText() {
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }
        Message message = choices.getFirst().message();
        if (message == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(message.content());
    }

    /**
     * Groq 토큰 사용량 → 공통 TokenUsage로 변환
     * usage가 없으면 0으로 채움
     */
    public AiResponse.TokenUsage toTokenUsage() {
        if (usage == null) {
            return new AiResponse.TokenUsage(0, 0, 0);
        }
        return new AiResponse.TokenUsage(
            usage.promptTokens(),
            usage.completionTokens(),
            usage.totalTokens()
        );
    }
}
