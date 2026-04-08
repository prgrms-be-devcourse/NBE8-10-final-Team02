package com.back.backend.domain.ai.client.vertexai;

import com.back.backend.domain.ai.client.*;
import com.back.backend.domain.ai.client.gemini.GeminiRequest;
import com.back.backend.domain.ai.client.gemini.GeminiResponse;
import com.back.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;

/**
 * Vertex AI (Gemini) API 구현체
 * Google AI Studio의 GeminiClient와 동일한 요청/응답 형식을 사용하되,
 * 인증 방식(Service Account OAuth2)과 엔드포인트 URL만 다름
 *
 * GeminiRequest/GeminiResponse를 100% 재사용하여 코드 중복을 최소화
 *
 * 429 처리: GeminiClient와 동일한 패턴
 *   - details[].metadata.quota_metric에 "daily"가 포함되면 DAILY 한도 소진
 *   - 그 외(또는 파싱 실패)는 보수적으로 MINUTE 초과로 처리
 */
public class VertexAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(VertexAiClient.class);
    /** 429 응답 body 파싱 전용 ObjectMapper (스레드 안전 — 공유 가능) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final VertexAiClientProperties properties;
    private final VertexAiTokenProvider tokenProvider;

    public VertexAiClient(RestClient restClient, VertexAiClientProperties properties, VertexAiTokenProvider tokenProvider) {
        this.restClient = restClient;
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public AiResponse call(AiRequest request) {
        // 공통 → Gemini 형식 변환 (AI Studio와 동일 형식) → 호출 → 텍스트 추출 → 공통 변환
        GeminiRequest geminiRequest = GeminiRequest.from(request);
        GeminiResponse geminiResponse = doCall(geminiRequest);

        String content = geminiResponse.extractText()
            .orElseThrow(() -> {
                log.warn("[VertexAI] 빈 응답 수신");
                return new AiClientException(
                    AiProvider.VERTEX_AI,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    "Vertex AI가 빈 응답을 반환했습니다"
                );
            });

        return new AiResponse(content, geminiResponse.toTokenUsage());
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.VERTEX_AI;
    }

    /**
     * Vertex AI에 generateContent 요청을 보내고 응답을 반환
     * baseUrl이 모델 경로까지 포함하므로 URI는 ":generateContent"만 append
     * 인증: Service Account OAuth2 Access Token (VertexAiTokenProvider가 자동 갱신)
     */
    private GeminiResponse doCall(GeminiRequest geminiRequest) {
        String accessToken;
        try {
            accessToken = tokenProvider.getAccessToken();
        } catch (IOException e) {
            log.error("[VertexAI] Access Token 발급 실패: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.VERTEX_AI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Vertex AI 인증 실패: " + e.getMessage(),
                e
            );
        }

        try {
            return restClient.post()
                .uri(":generateContent")
                .header("Authorization", "Bearer " + accessToken)
                .body(geminiRequest)
                .retrieve()
                .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                String body = e.getResponseBodyAsString();
                RateLimitType limitType = detectRateLimitType(body);
                int retryAfter = extractRetryAfter(e);

                if (limitType == RateLimitType.DAILY) {
                    log.warn("[VertexAI] 일간 API 한도 소진 (429 DAILY). retryAfter={}s, body={}", retryAfter, body);
                    throw new AiClientException(
                        AiProvider.VERTEX_AI,
                        ErrorCode.AI_DAILY_LIMIT_EXCEEDED,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "오늘 Vertex AI 일간 한도를 모두 소진했습니다. 내일 다시 시도해주세요.",
                        RateLimitType.DAILY,
                        retryAfter,
                        e
                    );
                } else {
                    log.warn("[VertexAI] 분당 API 한도 초과 (429 MINUTE). retryAfter={}s, body={}", retryAfter, body);
                    throw new AiClientException(
                        AiProvider.VERTEX_AI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.BAD_GATEWAY,
                        "Vertex AI API 호출 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.",
                        RateLimitType.MINUTE,
                        retryAfter,
                        e
                    );
                }
            }
            log.error("[VertexAI] API 에러 응답: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AiClientException(
                AiProvider.VERTEX_AI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Vertex AI API 에러: " + e.getStatusCode(),
                e
            );
        } catch (ResourceAccessException e) {
            log.error("[VertexAI] 네트워크 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.VERTEX_AI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Vertex AI 연결 실패: " + e.getMessage(),
                e
            );
        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("[VertexAI] 예상 외 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.VERTEX_AI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Vertex AI 호출 중 오류: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Vertex AI 429 응답 body를 파싱하여 rate limit 종류를 판별
     * Gemini API와 동일한 에러 구조: details[].metadata.quota_metric
     */
    private RateLimitType detectRateLimitType(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode details = root.path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    String quotaMetric = detail.path("metadata").path("quota_metric").asText("");
                    if (quotaMetric.contains("daily")) {
                        return RateLimitType.DAILY;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[VertexAI] 429 body 파싱 실패, MINUTE으로 처리: {}", e.getMessage());
        }
        return RateLimitType.MINUTE;
    }

    /**
     * Retry-After 헤더에서 재시도 대기 초를 추출
     * 헤더가 없거나 파싱 실패 시 기본값 60초 반환
     */
    private int extractRetryAfter(RestClientResponseException e) {
        try {
            String retryAfterHeader = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("Retry-After")
                : null;
            if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
                int parsed = Integer.parseInt(retryAfterHeader.trim());
                return Math.max(1, parsed);
            }
        } catch (Exception ex) {
            log.debug("[VertexAI] Retry-After 헤더 파싱 실패: {}", ex.getMessage());
        }
        return 60;
    }
}
