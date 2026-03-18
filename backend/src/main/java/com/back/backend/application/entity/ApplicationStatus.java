package com.back.backend.application.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum ApplicationStatus implements StringCodeEnum {
    DRAFT("draft"),
    READY("ready");

    private final String value;

    ApplicationStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class ApplicationStatusConverter extends StringCodeEnumConverter<ApplicationStatus> {

    ApplicationStatusConverter() {
        super(ApplicationStatus.class);
    }
}
