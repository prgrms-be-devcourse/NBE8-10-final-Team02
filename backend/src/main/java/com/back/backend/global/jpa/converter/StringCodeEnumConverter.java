package com.back.backend.global.jpa.converter;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

public abstract class StringCodeEnumConverter<E extends Enum<E> & StringCodeEnum> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected StringCodeEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> candidate.getValue().equals(dbData))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported enum value: " + dbData));
    }
}
