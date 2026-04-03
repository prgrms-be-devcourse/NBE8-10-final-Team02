package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 포트폴리오 요약 응답 검증기.
 */
public class PortfolioSummaryValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.portfolio.summary.v1";
    private static final String SCHEMA_FILE = "portfolio-summary.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public PortfolioSummaryValidator(JsonSchemaValidator jsonSchemaValidator) {
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public ValidationResult validate(JsonNode responseNode) {
        // schema 검증
        ValidationResult schemaResult = jsonSchemaValidator.validateSchema(responseNode, SCHEMA_FILE);
        if (!schemaResult.valid()) {
            return schemaResult;
        }

        // cross-field 검증
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 바뀐 JSON 구조에 맞게 단일 "project" 객체 추출
        JsonNode project = responseNode.get("project");

        // 배열(isArray)이 아니라 객체(isObject)인지 확인!
        if (project != null && project.isObject()) {

            // sourceRefs가 비어있는지 검사하여 경고 추가
            JsonNode sourceRefs = project.get("sourceRefs");
            if (sourceRefs != null && sourceRefs.isArray() && sourceRefs.isEmpty()) {
                warnings.add("sourceRefs가 비어있습니다");
                // (참고: 기존 validateSourceRefs 내부 로직을 이곳에 병합하거나,
                // 단일 객체용 메서드로 분리하여 호출하는 것이 깔끔합니다.)
            }

            // evidenceBullets 5개 초과 검사 (networknt 라이브러리 버그 우회)
            JsonNode evidenceBullets = project.get("evidenceBullets");
            if (evidenceBullets != null && evidenceBullets.isArray() && evidenceBullets.size() > 5) {
                errors.add("evidenceBullets는 5개를 초과할 수 없습니다.");
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        if (!warnings.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        }
        return ValidationResult.success();
    }

    /**
     * projectKey 중복 금지 — hard fail
     */
    private void validateProjectKeyUniqueness(JsonNode projects, List<String> errors) {
        Set<String> keys = new HashSet<>();
        for (JsonNode project : projects) {
            String key = project.get("projectKey").asText();
            if (!keys.add(key)) {
                errors.add("projectKey 중복: " + key);
            }
        }
    }

    /**
     * sourceRefs가 비어있으면 근거 부족 경고 — qualityFlags low_context에 해당
     */
    private void validateSourceRefs(JsonNode projects, List<String> warnings) {
        for (JsonNode project : projects) {
            JsonNode sourceRefs = project.get("sourceRefs");
            String key = project.get("projectKey").asText();
            if (sourceRefs == null || sourceRefs.isEmpty()) {
                warnings.add("sourceRefs가 비어있습니다: " + key);
            }
        }
    }
}
