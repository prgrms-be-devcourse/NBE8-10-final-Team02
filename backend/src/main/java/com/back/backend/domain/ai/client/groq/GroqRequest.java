package com.back.backend.domain.ai.client.groq;

import com.back.backend.domain.ai.client.AiRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Groq API 요청 DTO (OpenAI Chat Completions 호환 형식)
 * POST /openai/v1/chat/completions
 * Groq는 OpenAI API와 동일한 요청 구조를 사용
 */
public record GroqRequest(
    String model,
    List<Message> messages,
    double temperature,
    @JsonProperty("max_tokens") int maxTokens,
    @JsonProperty("response_format") ResponseFormat responseFormat
) {
    /**
     * 채팅 메시지 — role(system/user)과 content로 구성
     */
    public record Message(
        String role,
        String content
    ) {
    }

    /**
     * 응답 형식 지정 — JSON 모드 활성화용
     */
    public record ResponseFormat(
        String type
    ) {
    }

    /**
     * 공통 AiRequest → Groq(OpenAI 호환) 요청으로 변환
     * systemPrompt → system 메시지
     * developerPrompt + userMessage → user 메시지로 결합
     */
    public static GroqRequest from(AiRequest request, String model) {
        // system 역할: AI의 역할과 규칙 정의
        Message systemMessage = new Message("system", request.systemPrompt());

        // user 역할: 개발자 프롬프트 + 실제 입력 데이터 결합
        String userText = request.developerPrompt() + "\n\n" + request.userMessage();
        Message userMessage = new Message("user", userText);

        // JSON 모드 활성화 — 구조화된 응답 보장
        ResponseFormat jsonFormat = new ResponseFormat("json_object");

        return new GroqRequest(
            model,
            List.of(systemMessage, userMessage),
            request.temperature(),
            request.maxTokens(),
            jsonFormat
        );
    }
}
