package com.back.backend.domain.practice.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum PracticeSessionStatus implements StringCodeEnum {
    IN_PROGRESS("in_progress"),
    EVALUATED("evaluated"),
    FAILED("failed");

    private final String value;

    PracticeSessionStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class PracticeSessionStatusConverter extends StringCodeEnumConverter<PracticeSessionStatus> {

    PracticeSessionStatusConverter() {
        super(PracticeSessionStatus.class);
    }
}
