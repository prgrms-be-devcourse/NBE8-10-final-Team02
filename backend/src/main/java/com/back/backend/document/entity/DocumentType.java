package com.back.backend.document.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

public enum DocumentType implements StringCodeEnum {
    RESUME("resume"),
    AWARD("award"),
    CERTIFICATE("certificate"),
    OTHER("other");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}

@Converter(autoApply = false)
class DocumentTypeConverter extends StringCodeEnumConverter<DocumentType> {

    DocumentTypeConverter() {
        super(DocumentType.class);
    }
}
