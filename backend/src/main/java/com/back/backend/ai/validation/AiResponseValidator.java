package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 응답 검증 인터페이스
 * 각 템플릿마다 구현체 생성
 * 파이프라인에서 AI 응답을 저장하기 전에 이 인터페이스로 검증
 * <p>
 * JSON 파싱 → 최상위 object → 필수 필드 → enum → 길이/개수 → cross-field → DB 저장 가능 여부
 */
public interface AiResponseValidator {

    /**
     * AI 응답 JSON을 검증
     *
     * @param responseNode 파싱된 AI 응답 JSON
     * @return 검증 결과 — 성공 여부와 실패 시 사유
     */
    ValidationResult validate(JsonNode responseNode);

    /**
     * 이 검증기가 어떤 템플릿용인지 반환
     * 파이프라인에서 템플릿 ID로 적절한 검증기를 찾을 때 사용
     */
    String getTemplateId();
}
