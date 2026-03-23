package com.back.backend.domain.ai.client;

import com.back.backend.global.jpa.converter.StringCodeEnum;

/**
 * AI 모델 제공자.
 * 새 모델 추가 시 여기에 enum 값을 추가하고,
 * 해당 provider의 AiClient 구현체를 만들면 됨
 */
public enum AiProvider implements StringCodeEnum {
    GEMINI("gemini"),
    OPENAI("openai"),
    CLAUDE("claude");

    private final String value;

    AiProvider(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
