package com.back.backend.domain.document.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

/**
 * 문서의 텍스트 추출 진행 상태를 나타내는 enum.
 *
 * <p>문서 업로드 직후에는 {@code PENDING}으로 설정되고,
 * 이후 추출 파이프라인이 처리 결과에 따라 {@code SUCCESS} 또는 {@code FAILED}로 전환한다.</p>
 *
 * <p>DB에는 {@code getValue()}가 반환하는 lowercase String으로 저장된다.</p>
 */
public enum DocumentExtractStatus implements StringCodeEnum {
    /** 업로드 완료, 텍스트 추출 대기 중 */
    PENDING("pending"),
    /** 텍스트 추출 성공 */
    SUCCESS("success"),
    /** 텍스트 추출 실패 (파싱 오류 등) */
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

/** {@link DocumentExtractStatus}를 DB 컬럼 문자열로 상호 변환하는 JPA converter. */
@Converter(autoApply = false)
class DocumentExtractStatusConverter extends StringCodeEnumConverter<DocumentExtractStatus> {

    DocumentExtractStatusConverter() {
        super(DocumentExtractStatus.class);
    }
}
