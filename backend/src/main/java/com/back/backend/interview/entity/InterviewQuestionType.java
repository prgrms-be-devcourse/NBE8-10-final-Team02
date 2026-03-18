package com.back.backend.interview.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum InterviewQuestionType implements StringCodeEnum {
    EXPERIENCE("experience"),
    PROJECT("project"),
    TECHNICAL_CS("technical_cs"),
    TECHNICAL_STACK("technical_stack"),
    BEHAVIORAL("behavioral"),
    FOLLOW_UP("follow_up");

    private final String value;

    InterviewQuestionType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class InterviewQuestionTypeConverter extends StringCodeEnumConverter<InterviewQuestionType> {

    InterviewQuestionTypeConverter() {
        super(InterviewQuestionType.class);
    }
}
