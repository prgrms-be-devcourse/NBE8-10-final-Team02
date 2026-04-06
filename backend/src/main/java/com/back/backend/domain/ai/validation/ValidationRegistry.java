package com.back.backend.domain.ai.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 응답 검증기 레지스트리
 * templateId → AiResponseValidator 조회
 * 생성 후 불변 — 외부에서 추가/수정 불가
 */
public class ValidationRegistry {

    private final Map<String, AiResponseValidator> validators;

    private ValidationRegistry(Map<String, AiResponseValidator> validators) {
        this.validators = validators;
    }

    /**
     * templateId에 해당하는 검증기를 반환
     * AiPipeline에서 AI 응답 검증 시 사용
     *
     * @param templateId 프롬프트 템플릿 ID (예: "ai.portfolio.summary.v1")
     * @return 해당 템플릿의 AiResponseValidator
     * @throws IllegalArgumentException 등록되지 않은 templateId
     */
    public AiResponseValidator get(String templateId) {
        AiResponseValidator validator = validators.get(templateId);
        if (validator == null) {
            throw new IllegalArgumentException("등록되지 않은 templateId: " + templateId);
        }
        return validator;
    }

    /**
     * 기본 검증기가 모두 등록된 불변 레지스트리를 생성
     */
    public static ValidationRegistry createDefault(JsonSchemaValidator jsonSchemaValidator) {
        Map<String, AiResponseValidator> map = new HashMap<>();

        AiResponseValidator[] validators = {
            new PortfolioSummaryValidator(jsonSchemaValidator),
            new SelfIntroGenerateValidator(jsonSchemaValidator),
            new InterviewQuestionsGenerateValidator(jsonSchemaValidator),
            new InterviewFollowupGenerateValidator(jsonSchemaValidator),
            new InterviewCompletionFollowupValidator(jsonSchemaValidator),
            new InterviewEvaluateValidator(jsonSchemaValidator),
            new InterviewSummaryValidator(jsonSchemaValidator),
            // Batch 포트폴리오 요약 검증기 — BatchRepoSummaryGeneratorService에서 사용
            new BatchPortfolioSummaryValidator()
        };

        for (AiResponseValidator validator : validators) {
            map.put(validator.getTemplateId(), validator);
        }

        // Map.copyOf()로 불변 맵 생성 — 이후 수정 시도 시 UnsupportedOperationException
        return new ValidationRegistry(Map.copyOf(map));
    }
}
