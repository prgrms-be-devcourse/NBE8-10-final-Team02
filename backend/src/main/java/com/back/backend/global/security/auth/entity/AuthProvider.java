package com.back.backend.global.security.auth.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum AuthProvider implements StringCodeEnum {
    GITHUB("github"),
    GOOGLE("google"),
    KAKAO("kakao");

    private final String value;

    AuthProvider(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class AuthProviderConverter extends StringCodeEnumConverter<AuthProvider> {

    AuthProviderConverter() {
        super(AuthProvider.class);
    }
}
