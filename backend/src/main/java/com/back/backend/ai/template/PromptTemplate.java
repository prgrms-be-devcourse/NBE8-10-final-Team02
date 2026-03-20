package com.back.backend.ai.template;

/**
 * 프롬프트 템플릿 정의
 * 각 AI 기능(포트폴리오 요약, 자소서 생성 등)마다 하나의 템플릿이 존재
 *
 * @param templateId        템플릿 식별자
 * @param version           템플릿 버전
 * @param taskType          작업 유형
 * @param systemPromptFile  시스템 프롬프트 파일 경로
 * @param developerPromptFile 개발자 프롬프트 파일 경로
 * @param schemaFile        출력 JSON schema 파일 경로
 * @param temperature       생성 다양성
 * @param maxTokens         최대 출력 토큰 수
 * @param retryPolicy       재시도 정책
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
    RetryPolicy retryPolicy
) {
    /**
     * @param maxRetries   최대 재시도 횟수
     * @param allowFallback fallback 모델 전환 허용 여부 (MVP에서는 false)
     */
    public record RetryPolicy(
        int maxRetries,
        boolean allowFallback
    ) {
    }
}
