package com.back.backend.ai.validation;

import java.util.List;

/**
 * AI 응답 검증 결과
 *
 * @param valid    검증 통과 여부
 * @param errors   실패 사유 목록 — valid가 true면 빈 리스트
 * @param warnings 경고 목록 — 검증은 통과하지만 품질 이슈가 있는 경우
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult successWithWarnings(List<String> warnings) {
        return new ValidationResult(true, List.of(), List.copyOf(warnings));
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors), List.of());
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error), List.of());
    }
}
