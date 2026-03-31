package com.back.backend.domain.ai.pipeline;

import com.back.backend.domain.ai.client.*;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplate;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.validation.AiResponseValidator;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import com.back.backend.domain.ai.validation.ValidationResult;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * AI 파이프라인 오케스트레이터
 * 템플릿 조회 → 프롬프트 로딩 → AiRequest 생성 → AiClient 호출 → JSON 파싱 → 검증 → 결과 반환
 * 파싱/검증 실패 시 RetryPolicy.maxRetries만큼 재시도, 초과 시 ServiceException
 */
public class AiPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiPipeline.class);

    private final AiClientRouter router;
    private final PromptTemplateRegistry templateRegistry;
    private final ValidationRegistry validationRegistry;
    private final PromptLoader promptLoader;
    private final JsonSchemaValidator jsonSchemaValidator;

    /**
     * @param router              AI provider 라우터 — getDefault()로 기본 provider의 AiClient 반환
     * @param templateRegistry    6개 프롬프트 템플릿 조회 (templateId → PromptTemplate)
     * @param validationRegistry  6개 응답 검증기 조회 (templateId → AiResponseValidator)
     * @param promptLoader        classpath .txt 프롬프트 파일 로딩 (캐싱)
     * @param jsonSchemaValidator AI 응답 JSON 파싱 및 schema 검증
     */
    public AiPipeline(
        AiClientRouter router,
        PromptTemplateRegistry templateRegistry,
        ValidationRegistry validationRegistry,
        PromptLoader promptLoader,
        JsonSchemaValidator jsonSchemaValidator
    ) {
        this.router = router;
        this.templateRegistry = templateRegistry;
        this.validationRegistry = validationRegistry;
        this.promptLoader = promptLoader;
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    /**
     * AI 파이프라인 실행
     * 기본 provider 호출 실패(AiClientException) 시 fallback provider로 자동 전환
     *
     * @param templateId 사용할 프롬프트 템플릿 ID
     * @param payload    AI에 전달할 입력 데이터 (JSON 문자열)
     * @return 파싱 및 검증을 통과한 AI 응답 JsonNode
     * @throws ServiceException  maxRetries 초과 시
     * @throws AiClientException AI 호출 실패 시 (fallback도 실패하면 전파)
     */
    public JsonNode execute(String templateId, String payload) {
        PromptTemplate template = templateRegistry.get(templateId);
        AiResponseValidator validator = validationRegistry.get(templateId);

        String systemPrompt = promptLoader.load(template.systemPromptFile());
        String developerPrompt = promptLoader.load(template.developerPromptFile());

        AiRequest request = new AiRequest(
            systemPrompt,
            developerPrompt,
            payload,
            template.temperature(),
            template.maxTokens()
        );

        // 기본 provider로 시도
        try {
            return executeWithClient(router.getDefault(), request, template, validator, templateId);
        } catch (AiClientException e) {
            // fallback provider가 설정되어 있으면 전환 시도
            return router.getFallback()
                .map(fallbackClient -> {
                    log.warn("[Fallback] 기본 provider({}) 실패 → fallback provider({})로 전환. 원인: {}",
                        e.getProvider(), fallbackClient.getProvider(), e.getMessage());
                    return executeWithClient(fallbackClient, request, template, validator, templateId);
                })
                .orElseThrow(() -> e); // fallback 미설정 시 원래 예외 그대로 전파
        }
    }

    /**
     * 특정 AiClient로 호출 → 파싱 → 검증 재시도 루프 실행
     * 파싱/검증 실패만 재시도, AiClientException은 즉시 전파
     */
    private JsonNode executeWithClient(
        AiClient client,
        AiRequest request,
        PromptTemplate template,
        AiResponseValidator validator,
        String templateId
    ) {
        int maxRetries = template.retryPolicy().maxRetries();
        List<String> lastErrors = List.of();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // AiClientException은 재시도 없이 즉시 전파
            AiResponse response = client.call(request);

            // JSON 파싱
            JsonSchemaValidator.ParseResult parseResult = jsonSchemaValidator.parse(response.content());
            if (parseResult instanceof JsonSchemaValidator.ParseResult.Failure f) {
                lastErrors = List.of(f.error());
                log.warn("[{}] AI 응답 파싱 실패 (attempt={}/{}): templateId={}, error={}",
                    client.getProvider(), attempt + 1, maxRetries + 1, templateId, f.error());
                continue;
            }

            JsonNode responseNode = ((JsonSchemaValidator.ParseResult.Success) parseResult).node();

            // 검증
            ValidationResult result = validator.validate(responseNode);
            if (!result.valid()) {
                lastErrors = result.errors();
                log.warn("[{}] AI 응답 검증 실패 (attempt={}/{}): templateId={}, errors={}",
                    client.getProvider(), attempt + 1, maxRetries + 1, templateId, lastErrors);
                continue;
            }

            if (!result.warnings().isEmpty()) {
                log.warn("[{}] AI 응답 검증 경고: templateId={}, warnings={}",
                    client.getProvider(), templateId, result.warnings());
            }

            return responseNode;
        }

        throw new ServiceException(
            ErrorCode.INTERNAL_SERVER_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            String.format("AI 응답 검증 실패 - maxRetries 초과: provider=%s, templateId=%s, errors=%s",
                client.getProvider(), templateId, lastErrors)
        );
    }
}
