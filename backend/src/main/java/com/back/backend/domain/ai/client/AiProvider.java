package com.back.backend.domain.ai.client;

import com.back.backend.global.jpa.converter.StringCodeEnum;

/**
 * AI 모델 제공자.
 * 새 모델 추가 시 여기에 enum 값을 추가하고,
 * 해당 provider의 AiClient 구현체를 만들면 됨
 */
public enum AiProvider implements StringCodeEnum {
    GEMINI("gemini"),
    GROQ("groq"),
    OPENAI("openai"),
    CLAUDE("claude"),
    VERTEX_AI("vertex-ai");

    private final String value;

    AiProvider(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * DB 코드값(value)으로 AiProvider를 조회
     * valueOf()는 enum 상수명(VERTEX_AI)으로만 매칭되므로,
     * "vertex-ai" 같은 하이픈 포함 값은 이 메서드로 조회해야 한다
     *
     * @param value DB 코드값 (예: "gemini", "groq", "vertex-ai")
     * @return 매칭되는 AiProvider
     * @throws IllegalArgumentException 매칭되는 provider가 없을 때
     */
    public static AiProvider fromValue(String value) {
        for (AiProvider provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("알 수 없는 AI provider: " + value);
    }
}
