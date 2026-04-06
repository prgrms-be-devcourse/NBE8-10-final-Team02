package com.back.backend.domain.ai.client.gemini;

import com.back.backend.domain.ai.client.AiRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini API 요청 DTO
 *  POST /v1beta/models/{model}:generateContent 형식에 맞춤
 * <a href="https://ai.google.dev/api/generate-content">Gemini generateContent API</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(
    @JsonProperty("system_instruction") Content systemInstruction,
    List<Content> contents,
    @JsonProperty("generationConfig") GenerationConfig generationConfig
) {
    private static final String COMPLETE_PROMPT_MARKER = "TEMPLATE_MARKER: COMPLETE_FOLLOWUP";
    private static final Map<String, Object> COMPLETE_RESPONSE_JSON_SCHEMA = buildCompleteResponseJsonSchema();

    public record Content(
        String role,
        List<Part> parts
    ) {
    }

    public record Part(
        String text
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
        double temperature,
        @JsonProperty("maxOutputTokens") int maxOutputTokens,
        @JsonProperty("responseMimeType") String responseMimeType,
        @JsonProperty("responseJsonSchema") Object responseJsonSchema,
        @JsonProperty("thinkingConfig") ThinkingConfig thinkingConfig
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ThinkingConfig(
        @JsonProperty("thinkingBudget") Integer thinkingBudget
    ) {
    }

    /**
     * 공통 AiRequest → Gemini 전용 요청으로 변환
     * systemPrompt → system_instruction,
     * developerPrompt + userMessage를 하나의 user text로 매핑
     */
    public static GeminiRequest from(AiRequest request) {
        Content systemInstruction = new Content(null, List.of(new Part(request.systemPrompt())));

        String userText = request.developerPrompt() + "\n\n" + request.userMessage();
        List<Content> contents = List.of(new Content("user", List.of(new Part(userText))));

        GenerationConfig config = new GenerationConfig(
            request.temperature(),
            request.maxTokens(),
            "application/json",  // 모든 템플릿이 JSON 출력을 요구
            resolveResponseJsonSchema(request.developerPrompt()),
            resolveThinkingConfig(request.developerPrompt())
        );

        return new GeminiRequest(systemInstruction, contents, config);
    }

    private static Object resolveResponseJsonSchema(String developerPrompt) {
        if (developerPrompt == null || developerPrompt.isBlank()) {
            return null;
        }
        if (developerPrompt.contains(COMPLETE_PROMPT_MARKER)) {
            return COMPLETE_RESPONSE_JSON_SCHEMA;
        }
        return null;
    }

    private static ThinkingConfig resolveThinkingConfig(String developerPrompt) {
        if (developerPrompt == null || developerPrompt.isBlank()) {
            return null;
        }
        if (developerPrompt.contains(COMPLETE_PROMPT_MARKER)) {
            return new ThinkingConfig(0);
        }
        return null;
    }

    private static Map<String, Object> buildCompleteResponseJsonSchema() {
        return linkedMapOf(
            "type", "object",
            "description", "Completion-stage follow-up response.",
            "additionalProperties", false,
            "required", List.of("followUpQuestions", "qualityFlags"),
            "propertyOrdering", List.of("followUpQuestions", "qualityFlags"),
            "properties", linkedMapOf(
                "followUpQuestions", linkedMapOf(
                    "type", "array",
                    "description", "Return exactly one question when any unresolved gap remains; otherwise return an empty array.",
                    "maxItems", 1,
                    "items", linkedMapOf(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("questionType", "difficultyLevel", "questionText", "parentQuestionOrder"),
                        "propertyOrdering", List.of("questionType", "difficultyLevel", "questionText", "parentQuestionOrder"),
                        "properties", linkedMapOf(
                            "questionType", linkedMapOf(
                                "type", "string",
                                "enum", List.of("follow_up")
                            ),
                            "difficultyLevel", linkedMapOf(
                                "type", "string",
                                "enum", List.of("medium")
                            ),
                            "questionText", linkedMapOf(
                                "type", "string",
                                "description", "Use one short Korean question sentence."
                            ),
                            "parentQuestionOrder", linkedMapOf(
                                "type", "integer",
                                "minimum", 1
                            )
                        )
                    )
                ),
                "qualityFlags", linkedMapOf(
                    "type", "array",
                    "maxItems", 1,
                    "description", "Use [] or [\"low_context\"] only.",
                    "items", linkedMapOf(
                        "type", "string",
                        "enum", List.of("low_context")
                    )
                )
            )
        );
    }

    private static Map<String, Object> linkedMapOf(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("key/value 쌍이 맞지 않습니다.");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
