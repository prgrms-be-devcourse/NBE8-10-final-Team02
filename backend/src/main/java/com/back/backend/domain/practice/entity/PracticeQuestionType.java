package com.back.backend.domain.practice.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum PracticeQuestionType implements StringCodeEnum {
    CS("cs"),
    BEHAVIORAL("behavioral");

    private final String value;

    PracticeQuestionType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static PracticeQuestionType fromSourceKey(String sourceKey) {
        return "local-behavioral".equals(sourceKey) ? BEHAVIORAL : CS;
    }
}

@Converter(autoApply = false)
class PracticeQuestionTypeConverter extends StringCodeEnumConverter<PracticeQuestionType> {

    PracticeQuestionTypeConverter() {
        super(PracticeQuestionType.class);
    }
}
