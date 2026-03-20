package com.back.backend.ai.validation;

import java.util.List;

/**
 * AI 응답 검증 결과
 *
 * @param valid  검증 통과 여부
 * @param errors 실패 사유 목록 — valid가 true면 빈 리스트
 */
public record ValidationResult(
    boolean valid,
    List<String> errors
) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }
}
