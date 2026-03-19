package com.back.backend.domain.application.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum ApplicationToneOption implements StringCodeEnum {
    FORMAL("formal"),
    BALANCED("balanced"),
    CASUAL("casual");

    private final String value;

    ApplicationToneOption(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class ApplicationToneOptionConverter extends StringCodeEnumConverter<ApplicationToneOption> {

    ApplicationToneOptionConverter() {
        super(ApplicationToneOption.class);
    }
}
