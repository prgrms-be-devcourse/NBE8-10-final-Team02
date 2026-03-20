package com.back.backend.domain.interview.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum FeedbackTagCategory implements StringCodeEnum {
    CONTENT("content"),
    STRUCTURE("structure"),
    EVIDENCE("evidence"),
    COMMUNICATION("communication"),
    TECHNICAL("technical"),
    OTHER("other");

    private final String value;

    FeedbackTagCategory(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class FeedbackTagCategoryConverter extends StringCodeEnumConverter<FeedbackTagCategory> {

    FeedbackTagCategoryConverter() {
        super(FeedbackTagCategory.class);
    }
}
