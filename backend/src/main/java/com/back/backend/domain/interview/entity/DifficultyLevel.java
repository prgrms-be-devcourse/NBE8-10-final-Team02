package com.back.backend.domain.interview.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum DifficultyLevel implements StringCodeEnum {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String value;

    DifficultyLevel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class DifficultyLevelConverter extends StringCodeEnumConverter<DifficultyLevel> {

    DifficultyLevelConverter() {
        super(DifficultyLevel.class);
    }
}
