package com.back.backend.domain.document.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum DocumentExtractStatus implements StringCodeEnum {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    DocumentExtractStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class DocumentExtractStatusConverter extends StringCodeEnumConverter<DocumentExtractStatus> {

    DocumentExtractStatusConverter() {
        super(DocumentExtractStatus.class);
    }
}
