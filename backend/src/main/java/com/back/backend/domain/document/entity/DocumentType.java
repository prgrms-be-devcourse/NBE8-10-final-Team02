package com.back.backend.domain.document.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

/**
 * 업로드 문서의 종류를 나타내는 enum.
 *
 * <p>DB에는 {@code getValue()}가 반환하는 lowercase 문자열로 저장된다.
 * enum 상수명(예: RESUME)이 아닌 값(예: "resume")이 컬럼에 들어가므로
 * DB 데이터를 직접 조회할 때 참고할 것.</p>
 */
public enum DocumentType implements StringCodeEnum {
    /** 이력서 */
    RESUME("resume"),
    /** 수상 내역 */
    AWARD("award"),
    /** 자격증 */
    CERTIFICATE("certificate"),
    /** 그 외 문서 */
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

/** {@link DocumentType}을 DB 컬럼 문자열로 상호 변환하는 JPA converter. */
@Converter(autoApply = false)
class DocumentTypeConverter extends StringCodeEnumConverter<DocumentType> {

    DocumentTypeConverter() {
        super(DocumentType.class);
    }
}
