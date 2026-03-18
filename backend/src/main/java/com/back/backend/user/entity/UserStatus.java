package com.back.backend.user.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum UserStatus implements StringCodeEnum {
    ACTIVE("active"),
    WITHDRAWN("withdrawn");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class UserStatusConverter extends StringCodeEnumConverter<UserStatus> {

    UserStatusConverter() {
        super(UserStatus.class);
    }
}
