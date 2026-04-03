package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 배치 포트폴리오 요약 응답 검증기.
 *
 * <p>AI가 응답한 JSON 배열에 대해 아래를 검증한다:
 * <ol>
 *   <li>응답이 JSON 배열인지 확인</li>
 *   <li>각 원소에 {@code repoId}와 {@code project} 필드가 있는지 확인</li>
 *   <li>각 {@code project}에 필수 필드(projectKey, summary, stack, signals)가 있는지 확인</li>
 *   <li>{@code repoId} 중복 없는지 확인 (hard fail)</li>
 *   <li>{@code sourceRefs}가 비어있으면 근거 부족 경고</li>
 * </ol>
 */
public class BatchPortfolioSummaryValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.portfolio.summary.batch.v1";

    // 각 project 원소가 반드시 가져야 하는 필수 필드
    private static final List<String> REQUIRED_PROJECT_FIELDS = List.of(
            "projectKey", "projectName", "summary", "role",
            "stack", "signals", "evidenceBullets", "challenges",
            "techDecisions", "strengths", "risks"
    );

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public ValidationResult validate(JsonNode responseNode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 응답 타입 확인 — 단일 객체는 경고 후 1-element 배열처럼 검증
        final JsonNode nodeToValidate;
        if (responseNode.isObject()) {
            warnings.add("AI가 배열 대신 단일 객체로 응답했습니다. 1개 repo 배열로 간주하여 검증합니다.");
            nodeToValidate = responseNode; // 단일 객체를 배열 원소처럼 직접 검증
            return validateSingleElement(nodeToValidate, warnings);
        } else if (!responseNode.isArray()) {
            return ValidationResult.failure(
                    List.of("배치 응답은 JSON 배열이어야 합니다. 실제 타입: " + responseNode.getNodeType()));
        }

        // 2. 배열이 비어있으면 실패
        if (responseNode.isEmpty()) {
            return ValidationResult.failure(List.of("배치 응답 배열이 비어있습니다."));
        }

        Set<String> repoIds = new HashSet<>();

        for (int i = 0; i < responseNode.size(); i++) {
            JsonNode element = responseNode.get(i);

            // 3. 각 원소가 객체인지 확인
            if (!element.isObject()) {
                errors.add("배열[" + i + "] 원소가 객체가 아닙니다: " + element.getNodeType());
                continue;
            }

            // 4. repoId 필드 존재 확인
            JsonNode repoIdNode = element.get("repoId");
            if (repoIdNode == null || repoIdNode.asText().isBlank()) {
                errors.add("배열[" + i + "] repoId 필드가 없거나 비어있습니다.");
                continue;
            }

            // 5. repoId 중복 확인 (hard fail)
            String repoId = repoIdNode.asText();
            if (!repoIds.add(repoId)) {
                errors.add("repoId 중복: " + repoId);
            }

            // 6. project 필드 존재 확인
            JsonNode project = element.get("project");
            if (project == null || !project.isObject()) {
                errors.add("배열[" + i + "] (repoId=" + repoId + ") project 필드가 없거나 객체가 아닙니다.");
                continue;
            }

            // 7. project 필수 필드 검증
            validateProjectFields(project, repoId, errors, warnings);
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
     * 단일 객체 응답(배열 없이 온 경우)을 배열 원소처럼 검증한다.
     */
    private ValidationResult validateSingleElement(JsonNode element, List<String> warnings) {
        List<String> errors = new ArrayList<>();

        JsonNode repoIdNode = element.get("repoId");
        if (repoIdNode == null || repoIdNode.asText().isBlank()) {
            return ValidationResult.failure(List.of("단일 객체 응답에 repoId 필드가 없거나 비어있습니다."));
        }
        String repoId = repoIdNode.asText();

        JsonNode project = element.get("project");
        if (project == null || !project.isObject()) {
            return ValidationResult.failure(List.of("단일 객체 응답 (repoId=" + repoId + ") project 필드가 없거나 객체가 아닙니다."));
        }

        validateProjectFields(project, repoId, errors, warnings);

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        if (!warnings.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        }
        return ValidationResult.success();
    }

    /**
     * 단일 project 객체의 필수 필드 및 cross-field 규칙을 검증한다.
     */
    private void validateProjectFields(JsonNode project, String repoId,
                                        List<String> errors, List<String> warnings) {
        // 필수 필드 존재 확인
        for (String field : REQUIRED_PROJECT_FIELDS) {
            if (project.get(field) == null) {
                errors.add("project[repoId=" + repoId + "] 필수 필드 누락: " + field);
            }
        }

        // projectKey 형식 확인 (영문 소문자 + 하이픈)
        JsonNode projectKey = project.get("projectKey");
        if (projectKey != null && !projectKey.asText().matches("[a-z0-9\\-]+")) {
            warnings.add("project[repoId=" + repoId + "] projectKey 형식 비권장: " + projectKey.asText());
        }

        // sourceRefs가 비어있으면 근거 부족 경고
        JsonNode sourceRefs = project.get("sourceRefs");
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            warnings.add("project[repoId=" + repoId + "] sourceRefs가 비어있습니다 (low_context 가능성).");
        }

        // challenges가 있으면 evidenceBullets도 있어야 함
        JsonNode challenges = project.get("challenges");
        JsonNode evidenceBullets = project.get("evidenceBullets");
        if (challenges != null && challenges.isArray() && !challenges.isEmpty()) {
            if (evidenceBullets == null || evidenceBullets.isEmpty()) {
                warnings.add("project[repoId=" + repoId + "] challenges가 있는데 evidenceBullets가 비어있습니다.");
            }
        }
    }
}
