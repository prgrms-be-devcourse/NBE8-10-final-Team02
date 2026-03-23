package com.back.backend.domain.document.entity;

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

    public static DocumentType fromValue(String value) {
        for (DocumentType documentType : values()) {
            if (documentType.value.equalsIgnoreCase(value)) {
                return documentType;
            }
        }

        throw new IllegalArgumentException("Unsupported document type: " + value);
    }
}

@Converter(autoApply = false)
class DocumentTypeConverter extends StringCodeEnumConverter<DocumentType> {

    DocumentTypeConverter() {
        super(DocumentType.class);
    }
}
