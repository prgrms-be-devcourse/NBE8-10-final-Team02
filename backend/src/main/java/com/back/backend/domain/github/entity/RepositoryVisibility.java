package com.back.backend.domain.github.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum RepositoryVisibility implements StringCodeEnum {
    PUBLIC("public"),
    PRIVATE("private"),
    INTERNAL("internal");

    private final String value;

    RepositoryVisibility(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class RepositoryVisibilityConverter extends StringCodeEnumConverter<RepositoryVisibility> {

    RepositoryVisibilityConverter() {
        super(RepositoryVisibility.class);
    }
}
