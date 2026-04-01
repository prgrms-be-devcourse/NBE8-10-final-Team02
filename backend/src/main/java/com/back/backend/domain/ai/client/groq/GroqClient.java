package com.back.backend.domain.ai.client.groq;

import com.back.backend.domain.ai.client.*;
import com.back.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Groq API 구현체 (OpenAI Chat Completions 호환)
 * 공통 AiRequest를 OpenAI 형식으로 변환하여 Groq에 호출하고 응답을 공통 AiResponse로 변환하여 반환
 * Gemini 할당량 초과 시 fallback으로 사용됨
 *
 * 429 처리:
 *   - x-ratelimit-remaining-requests 헤더가 "0"이면 DAILY 한도 소진
 *   - 그 외는 MINUTE 초과로 처리
 */
public class GroqClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final RestClient restClient;
    private final GroqClientProperties properties;

    public GroqClient(RestClient restClient, GroqClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AiResponse call(AiRequest request) {
        // 공통 → Groq(OpenAI 호환) 변환 → 호출 → 텍스트 추출 → 공통 변환
        GroqRequest groqRequest = GroqRequest.from(request, properties.model());
        GroqResponse groqResponse = doCall(groqRequest);

        String content = groqResponse.extractText()
            .orElseThrow(() -> {
                log.warn("[Groq] 빈 응답 수신");
                return new AiClientException(
                    AiProvider.GROQ,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    "Groq API가 빈 응답을 반환했습니다"
                );
            });

        return new AiResponse(content, groqResponse.toTokenUsage());
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GROQ;
    }

    private GroqResponse doCall(GroqRequest groqRequest) {
        try {
            return restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey())
                .body(groqRequest)
                .retrieve()
                .body(GroqResponse.class);
        } catch (RestClientResponseException e) {
            // 429 할당량 초과 — MINUTE vs DAILY 판별 후 각기 다른 ErrorCode로 throw
            if (e.getStatusCode().value() == 429) {
                RateLimitType limitType = detectRateLimitType(e);
                int retryAfter = extractRetryAfter(e);

                if (limitType == RateLimitType.DAILY) {
                    log.warn("[Groq] 일간 API 한도 소진 (429 DAILY). retryAfter={}s, body={}", retryAfter, e.getResponseBodyAsString());
                    throw new AiClientException(
                        AiProvider.GROQ,
                        ErrorCode.AI_DAILY_LIMIT_EXCEEDED,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "오늘 Groq API 일간 한도를 모두 소진했습니다. 내일 오전 9시(KST) 이후 다시 시도해주세요.",
                        RateLimitType.DAILY,
                        retryAfter,
                        e
                    );
                } else {
                    log.warn("[Groq] 분당 API 한도 초과 (429 MINUTE). retryAfter={}s, body={}", retryAfter, e.getResponseBodyAsString());
                    throw new AiClientException(
                        AiProvider.GROQ,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.BAD_GATEWAY,
                        "Groq API 호출 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.",
                        RateLimitType.MINUTE,
                        retryAfter,
                        e
                    );
                }
            }
            // 그 외 4xx/5xx 응답
            log.error("[Groq] API 에러 응답: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AiClientException(
                AiProvider.GROQ,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Groq API 에러: " + e.getStatusCode(),
                e
            );
        } catch (ResourceAccessException e) {
            // 타임아웃 또는 네트워크 연결 실패
            log.error("[Groq] 네트워크 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.GROQ,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Groq API 연결 실패: " + e.getMessage(),
                e
            );
        } catch (AiClientException e) {
            // 이미 변환된 예외는 그대로 전파
            throw e;
        } catch (Exception e) {
            // JSON 파싱 실패 등 예상 외 오류
            log.error("[Groq] 예상 외 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.GROQ,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Groq API 호출 중 오류: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Groq 429 응답 헤더로 rate limit 종류를 판별
     * x-ratelimit-remaining-requests 헤더가 "0"이면 일간 요청 한도 소진 (DAILY)
     * 헤더가 없거나 0이 아니면 분당 토큰/요청 초과 (MINUTE)
     *
     * @param e RestClientResponseException (429)
     * @return DAILY 또는 MINUTE
     */
    private RateLimitType detectRateLimitType(RestClientResponseException e) {
        try {
            String remaining = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("x-ratelimit-remaining-requests")
                : null;
            if ("0".equals(remaining)) {
                return RateLimitType.DAILY;
            }
        } catch (Exception ex) {
            log.debug("[Groq] rate limit 헤더 파싱 실패: {}", ex.getMessage());
        }
        return RateLimitType.MINUTE;
    }

    /**
     * retry-after 헤더에서 재시도 대기 초를 추출
     * 헤더가 없거나 파싱 실패 시 기본값 60초 반환
     *
     * @param e RestClientResponseException (429)
     * @return 재시도 대기 초 (최소 1, 기본 60)
     */
    private int extractRetryAfter(RestClientResponseException e) {
        try {
            String retryAfterHeader = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("retry-after")
                : null;
            if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
                int parsed = Integer.parseInt(retryAfterHeader.trim());
                return Math.max(1, parsed);
            }
        } catch (Exception ex) {
            log.debug("[Groq] retry-after 헤더 파싱 실패: {}", ex.getMessage());
        }
        return 60; // 기본 대기 시간
    }
}
