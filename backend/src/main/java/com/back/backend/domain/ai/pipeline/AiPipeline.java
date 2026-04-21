package com.back.backend.domain.ai.pipeline;

import com.back.backend.domain.ai.client.*;
import com.back.backend.domain.ai.recovery.TruncatedJsonArrayRecovery;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplate;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.usage.AiUsageRecorder;
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

import java.util.List;

/**
 * AI 파이프라인 오케스트레이터
 * 템플릿 조회 → 프롬프트 로딩 → AiRequest 생성 → AiClient 호출 → JSON 파싱 → 검증 → 결과 반환
 *
 * Provider 선택 전략:
 *   1. 템플릿의 preferredProvider가 지정되어 있으면 해당 provider 사용
 *   2. preferredProvider 미지정 또는 미등록이면 글로벌 기본 provider(ai.provider) 사용
 *   3. rate limit(429) 발생 시 글로벌 fallback provider(ai.fallback-provider)로 전환
 *
 * 파싱/검증 실패 시 RetryPolicy.maxRetries만큼 재시도, 초과 시 ServiceException
 * 성공/rate limit hit 시 AiUsageRecorder에 사용량 기록
 */
public class AiPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiPipeline.class);

    private final AiClientRouter router;
    private final PromptTemplateRegistry templateRegistry;
    private final ValidationRegistry validationRegistry;
    private final PromptLoader promptLoader;
    private final JsonSchemaValidator jsonSchemaValidator;
    /** AI 호출 성공/실패 사용량 기록기 */
    private final AiUsageRecorder usageRecorder;
    /** 시스템 전체 AI 동시 호출 수 제한 */
    private final AiConcurrencyLimiter concurrencyLimiter;
    /** 절단된 JSON 배열에서 완성 원소를 복구 */
    private final TruncatedJsonArrayRecovery partialRecovery;

    /**
     * @param router              AI provider 라우터 — getClient(provider)로 템플릿별 provider, getDefault()로 기본 provider 반환
     * @param templateRegistry    프롬프트 템플릿 조회 (templateId → PromptTemplate, preferredProvider 포함)
     * @param validationRegistry  응답 검증기 조회 (templateId → AiResponseValidator)
     * @param promptLoader        classpath .txt 프롬프트 파일 로딩 (캐싱)
     * @param jsonSchemaValidator AI 응답 JSON 파싱 및 schema 검증
     * @param usageRecorder       AI 호출 결과를 인메모리+DB에 기록
     * @param concurrencyLimiter  시스템 전체 AI 동시 호출 수 제한 (Semaphore 기반 Bulkhead)
     * @param partialRecovery     절단된 JSON 배열에서 완성 원소를 복구
     */
    public AiPipeline(
        AiClientRouter router,
        PromptTemplateRegistry templateRegistry,
        ValidationRegistry validationRegistry,
        PromptLoader promptLoader,
        JsonSchemaValidator jsonSchemaValidator,
        AiUsageRecorder usageRecorder,
        AiConcurrencyLimiter concurrencyLimiter,
        TruncatedJsonArrayRecovery partialRecovery
    ) {
        this.router = router;
        this.templateRegistry = templateRegistry;
        this.validationRegistry = validationRegistry;
        this.promptLoader = promptLoader;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.usageRecorder = usageRecorder;
        this.concurrencyLimiter = concurrencyLimiter;
        this.partialRecovery = partialRecovery;
    }

    /**
     * AI 파이프라인 실행 — maxTokens를 runtime에 override
     *
     * <p>배치 템플릿처럼 provider별로 출력 토큰을 동적으로 설정해야 할 때 사용.
     * 템플릿의 나머지 설정(temperature, retryPolicy 등)은 그대로 유지된다.
     *
     * @param templateId        사용할 프롬프트 템플릿 ID
     * @param payload           AI에 전달할 입력 데이터
     * @param maxTokensOverride 이 호출에만 적용할 출력 토큰 상한
     * @return 파싱 및 검증을 통과한 AI 응답 JsonNode
     */
    public JsonNode executeWithMaxTokens(String templateId, String payload, int maxTokensOverride) {
        PromptTemplate base = templateRegistry.get(templateId);
        PromptTemplate overrideTemplate = base.withMaxTokens(maxTokensOverride);
        AiResponseValidator validator = validationRegistry.get(templateId);

        String systemPrompt = promptLoader.load(overrideTemplate.systemPromptFile());
        String developerPrompt = promptLoader.load(overrideTemplate.developerPromptFile());

        AiRequest request = new AiRequest(
            systemPrompt, developerPrompt, payload,
            overrideTemplate.temperature(), overrideTemplate.maxTokens()
        );

        return concurrencyLimiter.executeWithLimit(() ->
            executeWithFallbackChain(resolveClient(overrideTemplate), request, overrideTemplate, validator, templateId)
        );
    }

    /**
     * AI 파이프라인 실행 — 직무별 overlay 프롬프트 합성 지원
     * base developerPrompt에 roleOverlayFile을 append하여 직무별 평가 기준을 적용
     *
     * @param templateId      사용할 프롬프트 템플릿 ID
     * @param payload         AI에 전달할 입력 데이터 (JSON 문자열)
     * @param roleOverlayFile 직무별 overlay 프롬프트 파일 경로 (null이면 overlay 없이 실행)
     * @return 파싱 및 검증을 통과한 AI 응답 JsonNode
     */
    public JsonNode execute(String templateId, String payload, String roleOverlayFile) {
        PromptTemplate template = templateRegistry.get(templateId);
        AiResponseValidator validator = validationRegistry.get(templateId);

        String systemPrompt = promptLoader.load(template.systemPromptFile());
        String developerPrompt = promptLoader.loadComposite(
            template.developerPromptFile(), roleOverlayFile
        );

        AiRequest request = new AiRequest(
            systemPrompt, developerPrompt, payload,
            template.temperature(), template.maxTokens()
        );

        return concurrencyLimiter.executeWithLimit(() ->
            executeWithFallbackChain(resolveClient(template), request, template, validator, templateId)
        );
    }

    /**
     * AI 파이프라인 실행
     *
     * @param templateId 사용할 프롬프트 템플릿 ID
     * @param payload    AI에 전달할 입력 데이터 (JSON 문자열)
     * @return 파싱 및 검증을 통과한 AI 응답 JsonNode
     * @throws ServiceException  maxRetries 초과 시
     * @throws AiClientException AI 호출 실패 시 (chain 전체 소진 시 전파)
     */
    public JsonNode execute(String templateId, String payload) {
        PromptTemplate template = templateRegistry.get(templateId);
        AiResponseValidator validator = validationRegistry.get(templateId);

        String systemPrompt = promptLoader.load(template.systemPromptFile());
        String developerPrompt = promptLoader.load(template.developerPromptFile());

        AiRequest request = new AiRequest(
            systemPrompt, developerPrompt, payload,
            template.temperature(), template.maxTokens()
        );

        return concurrencyLimiter.executeWithLimit(() ->
            executeWithFallbackChain(resolveClient(template), request, template, validator, templateId)
        );
    }

    /**
     * preferred provider 실패 시 템플릿의 fallbackChain을 순서대로 시도
     * fallbackChain이 비어있으면 즉시 예외 전파
     * 어떤 예외 종류든(400, 429, 5xx) fallback 발동 — chain 전체 소진 시 마지막 예외 전파
     */
    private JsonNode executeWithFallbackChain(
        AiClient primary,
        AiRequest request,
        PromptTemplate template,
        AiResponseValidator validator,
        String templateId
    ) {
        List<AiProvider> chain = template.retryPolicy().fallbackChain();

        try {
            return executeWithClient(primary, request, template, validator, templateId);
        } catch (AiClientException e) {
            if (chain.isEmpty()) {
                throw e;
            }
            log.warn("[Fallback] provider({}) 실패 → fallback chain {} 시도. 원인: {}",
                e.getProvider(), chain, e.getMessage());
        }

        AiClientException lastException = null;
        for (AiProvider fallbackProvider : chain) {
            try {
                AiClient fallbackClient = router.getClient(fallbackProvider);
                log.info("[Fallback] provider({}) 시도", fallbackProvider);
                return executeWithClient(fallbackClient, request, template, validator, templateId);
            } catch (AiClientException e) {
                log.warn("[Fallback] provider({}) 실패. 원인: {}", e.getProvider(), e.getMessage());
                lastException = e;
            }
        }

        throw lastException;
    }

    /**
     * 템플릿의 preferredProvider에 해당하는 AiClient를 반환
     * preferredProvider가 null이거나 해당 provider가 등록되지 않은 경우 글로벌 기본 provider로 fallback
     * test 환경에서 VertexAI 미등록 시에도 안전하게 동작
     */
    private AiClient resolveClient(PromptTemplate template) {
        if (template.preferredProvider() != null) {
            try {
                return router.getClient(template.preferredProvider());
            } catch (IllegalArgumentException e) {
                log.warn("[AiPipeline] preferredProvider({}) 미등록, default provider로 fallback",
                    template.preferredProvider());
                return router.getDefault();
            }
        }
        return router.getDefault();
    }

    /**
     * 특정 AiClient로 호출 → 파싱 → 검증 재시도 루프 실행
     * 파싱/검증 실패만 재시도, AiClientException은 즉시 전파
     * 성공 시 usageRecorder.recordSuccess(), AiClientException 시 recordRateLimitHit() 호출
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
        String lastRawContent = null; // partial recovery를 위한 원본 응답 보존

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            AiResponse response;
            try {
                // AiClientException은 재시도 없이 즉시 전파
                response = client.call(request);
            } catch (AiClientException e) {
                // rate limit hit 사용량 기록 (rateLimitType이 null이면 내부에서 skip)
                usageRecorder.recordRateLimitHit(client.getProvider(), e.getRateLimitType());
                throw e;
            }

            // 성공 시 사용량 기록
            usageRecorder.recordSuccess(client.getProvider(), response.tokenUsage());

            // JSON 파싱
            JsonSchemaValidator.ParseResult parseResult = jsonSchemaValidator.parse(response.content());
            if (parseResult instanceof JsonSchemaValidator.ParseResult.Failure f) {
                lastRawContent = response.content(); // 절단된 원본 보존
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

        // ── Partial Recovery: JSON 절단 시 완성된 원소만 복구 ──────────────
        if (lastRawContent != null && template.allowPartialRecovery()) {
            final List<String> capturedErrors = lastErrors; // lambda capture용 effectively final 복사
            return partialRecovery.tryRecover(lastRawContent)
                .map(partial -> {
                    log.warn("[{}] Partial recovery 성공: {}개 원소 복구. templateId={}",
                        client.getProvider(), partial.size(), templateId);
                    return partial;
                })
                .orElseThrow(() -> {
                    log.warn("[{}] Partial recovery 실패: 복구 가능한 원소 없음. templateId={}",
                        client.getProvider(), templateId);
                    return new ServiceException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        String.format("AI 응답 검증 실패 - maxRetries 초과: provider=%s, templateId=%s, errors=%s",
                            client.getProvider(), templateId, capturedErrors));
                });
        }

        throw new ServiceException(
            ErrorCode.INTERNAL_SERVER_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            String.format("AI 응답 검증 실패 - maxRetries 초과: provider=%s, templateId=%s, errors=%s",
                client.getProvider(), templateId, lastErrors)
        );
    }
}
