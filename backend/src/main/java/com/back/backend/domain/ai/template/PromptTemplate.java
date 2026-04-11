package com.back.backend.domain.ai.template;

import com.back.backend.domain.ai.client.AiProvider;

import java.util.List;

/**
 * 프롬프트 템플릿 정의
 * 각 AI 기능(포트폴리오 요약, 자소서 생성 등)마다 하나의 템플릿이 존재
 *
 * @param templateId          템플릿 식별자
 * @param version             템플릿 버전
 * @param taskType            작업 유형
 * @param systemPromptFile    시스템 프롬프트 파일 경로
 * @param developerPromptFile 개발자 프롬프트 파일 경로
 * @param schemaFile          출력 JSON schema 파일 경로
 * @param temperature         생성 다양성
 * @param maxTokens           최대 출력 토큰 수
 * @param retryPolicy         재시도 정책
 * @param preferredProvider    이 템플릿이 우선 사용할 AI provider (null이면 글로벌 기본 provider 사용)
 * @param allowPartialRecovery true이면 JSON 절단 시 완성된 원소만 추출해 부분 저장 시도
 */
public record PromptTemplate(
    String templateId,
    String version,
    String taskType,
    String systemPromptFile,
    String developerPromptFile,
    String schemaFile,
    double temperature,
    int maxTokens,
    RetryPolicy retryPolicy,
    AiProvider preferredProvider,
    boolean allowPartialRecovery
) {
    public PromptTemplate {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId는 필수입니다");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version은 필수입니다");
        }
        if (temperature < 0.0 || temperature > 1.0) {
            throw new IllegalArgumentException("temperature는 0.0~1.0 범위여야 합니다");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens는 0보다 커야 합니다");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy는 필수입니다");
        }
    }

    /** maxTokens만 교체한 새 PromptTemplate 반환 (runtime override용) */
    public PromptTemplate withMaxTokens(int newMaxTokens) {
        return new PromptTemplate(templateId, version, taskType, systemPromptFile,
            developerPromptFile, schemaFile, temperature, newMaxTokens,
            retryPolicy, preferredProvider, allowPartialRecovery);
    }

    public record RetryPolicy(
        int maxRetries,
        List<AiProvider> fallbackChain
    ) {
        public RetryPolicy {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries는 0 이상이어야 합니다");
            }
            if (fallbackChain == null) {
                fallbackChain = List.of();
            }
        }
    }
}
