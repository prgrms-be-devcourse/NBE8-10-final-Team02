package com.back.backend.github.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum GithubSyncStatus implements StringCodeEnum {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    GithubSyncStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class GithubSyncStatusConverter extends StringCodeEnumConverter<GithubSyncStatus> {

    GithubSyncStatusConverter() {
        super(GithubSyncStatus.class);
    }
}
