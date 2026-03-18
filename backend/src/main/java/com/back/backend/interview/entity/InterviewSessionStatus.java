package com.back.backend.interview.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum InterviewSessionStatus implements StringCodeEnum {
    READY("ready"),
    IN_PROGRESS("in_progress"),
    PAUSED("paused"),
    COMPLETED("completed"),
    FEEDBACK_COMPLETED("feedback_completed");

    private final String value;

    InterviewSessionStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class InterviewSessionStatusConverter extends StringCodeEnumConverter<InterviewSessionStatus> {

    InterviewSessionStatusConverter() {
        super(InterviewSessionStatus.class);
    }
}
