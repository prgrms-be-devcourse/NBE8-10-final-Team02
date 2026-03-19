package com.back.backend.ai.client.gemini;

import com.back.backend.ai.client.AiRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Gemini API 요청 DTO
 * <a href="https://ai.google.dev/api/generate-content">Gemini generateContent API</a>
 */
public record GeminiRequest(
    @JsonProperty("system_instruction") Content systemInstruction,
    List<Content> contents,
    @JsonProperty("generationConfig") GenerationConfig generationConfig
) {
    public record Content(
        String role,
        List<Part> parts
    ) {
    }

    public record Part(
        String text
    ) {
    }

    public record GenerationConfig(
        double temperature,
        @JsonProperty("maxOutputTokens") int maxOutputTokens,
        @JsonProperty("responseMimeType") String responseMimeType
    ) {
    }

    /**
     * 공통 AiRequest → Gemini 전용 요청으로 변환
     * systemPrompt → system_instruction,
     * developerPrompt + userMessage → contents 배열로 매핑
     */
    public static GeminiRequest from(AiRequest request) {
        Content systemInstruction = new Content(null, List.of(new Part(request.systemPrompt())));

        // developerPrompt와 userMessage를 하나의 user content로 결합
        String userText = request.developerPrompt() + "\n\n" + request.userMessage();
        List<Content> contents = List.of(new Content("user", List.of(new Part(userText))));

        GenerationConfig config = new GenerationConfig(
            request.temperature(),
            request.maxTokens(),
            "application/json"  // 모든 템플릿이 JSON 출력을 요구
        );

        return new GeminiRequest(systemInstruction, contents, config);
    }
}
