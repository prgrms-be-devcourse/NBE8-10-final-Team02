package com.back.backend.domain.application.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum ApplicationLengthOption implements StringCodeEnum {
    SHORT("short"),
    MEDIUM("medium"),
    LONG("long");

    private final String value;

    ApplicationLengthOption(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class ApplicationLengthOptionConverter extends StringCodeEnumConverter<ApplicationLengthOption> {

    ApplicationLengthOptionConverter() {
        super(ApplicationLengthOption.class);
    }
}
