package com.back.backend.domain.ai.client.gemini;

import com.back.backend.domain.ai.client.*;
import com.back.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Gemini API 구현체
 * 공통 AiRequest를 Gemini 형식으로 변환하여 호출하고
 * 응답을 공통 AiResponse로 변환하여 반환
 *
 * 429 처리:
 *   - details[].metadata.quota_metric에 "daily"가 포함되면 DAILY 한도 소진
 *   - 그 외(또는 파싱 실패)는 보수적으로 MINUTE 초과로 처리
 */
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    /** 429 응답 body 파싱 전용 ObjectMapper (스레드 안전 — 공유 가능) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final GeminiClientProperties properties;

    public GeminiClient(RestClient restClient, GeminiClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AiResponse call(AiRequest request) {
        // 공통 → Gemini 변환 → 호출 → 텍스트 추출 → 공통 변환
        GeminiRequest geminiRequest = GeminiRequest.from(request);
        GeminiResponse geminiResponse = doCall(geminiRequest);
        logResponseSummary(geminiResponse);

        String content = geminiResponse.extractText()
            .orElseThrow(() -> {
                log.warn("[Gemini] 빈 응답 수신");
                return new AiClientException(
                    AiProvider.GEMINI,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    "Gemini API가 빈 응답을 반환했습니다"
                );
            });

        return new AiResponse(content, geminiResponse.toTokenUsage());
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GEMINI;
    }

    private GeminiResponse doCall(GeminiRequest geminiRequest) {
        String uri = "/models/" + properties.model() + ":generateContent";

        try {
            return restClient.post()
                .uri(uri)
                .header("x-goog-api-key", properties.apiKey())
                .body(geminiRequest)
                .retrieve()
                .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            // 429 할당량 초과 — MINUTE vs DAILY 판별 후 각기 다른 ErrorCode로 throw
            if (e.getStatusCode().value() == 429) {
                String body = e.getResponseBodyAsString();
                RateLimitType limitType = detectRateLimitType(body);
                int retryAfter = extractRetryAfter(e);

                if (limitType == RateLimitType.DAILY) {
                    log.warn("[Gemini] 일간 API 한도 소진 (429 DAILY). retryAfter={}s, body={}", retryAfter, body);
                    throw new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.AI_DAILY_LIMIT_EXCEEDED,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "오늘 Gemini API 일간 한도를 모두 소진했습니다. 내일 오전 9시(KST) 이후 다시 시도해주세요.",
                        RateLimitType.DAILY,
                        retryAfter,
                        e
                    );
                } else {
                    log.warn("[Gemini] 분당 API 한도 초과 (429 MINUTE). retryAfter={}s, body={}", retryAfter, body);
                    throw new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.BAD_GATEWAY,
                        "AI API 호출 횟수가 부족합니다. 잠시 후 다시 시도해주세요.",
                        RateLimitType.MINUTE,
                        retryAfter,
                        e
                    );
                }
            }
            // 그 외 4xx/5xx 응답
            log.error("[Gemini] API 에러 응답: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AiClientException(
                AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Gemini API 에러: " + e.getStatusCode(),
                e
            );
        } catch (ResourceAccessException e) {
            // 타임아웃 또는 네트워크 연결 실패
            log.error("[Gemini] 네트워크 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Gemini API 연결 실패: " + e.getMessage(),
                e
            );
        } catch (AiClientException e) {
            // 이미 변환된 예외는 그대로 전파 (위 블록에서 throw된 경우)
            throw e;
        } catch (Exception e) {
            // JSON 파싱 실패 등 예상 외 오류
            log.error("[Gemini] 예상 외 오류: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Gemini API 호출 중 오류: " + e.getMessage(),
                e
            );
        }
    }

    private void logResponseSummary(GeminiResponse geminiResponse) {
        if (!log.isDebugEnabled()) {
            return;
        }

        List<GeminiResponse.Candidate> candidates = geminiResponse.candidates();
        int candidateCount = candidates == null ? 0 : candidates.size();
        if (candidateCount == 0) {
            log.debug("[Gemini] 응답 요약: candidates=0");
            return;
        }

        GeminiResponse.Candidate firstCandidate = candidates.getFirst();
        GeminiResponse.Content content = firstCandidate != null ? firstCandidate.content() : null;
        List<GeminiResponse.Part> parts = content != null ? content.parts() : null;
        int partsCount = parts == null ? 0 : parts.size();

        StringBuilder partLengths = new StringBuilder("[");
        StringBuilder combinedText = new StringBuilder();
        String lastPartText = null;

        if (parts != null) {
            for (int i = 0; i < parts.size(); i++) {
                GeminiResponse.Part part = parts.get(i);
                String text = part != null ? part.text() : null;
                if (i > 0) {
                    partLengths.append(", ");
                }
                partLengths.append(text == null ? "null" : text.length());

                if (text != null) {
                    combinedText.append(text);
                    lastPartText = text;
                }
            }
        }
        partLengths.append(']');

        String merged = combinedText.toString();
        log.debug(
            "[Gemini] 응답 요약: candidates={}, parts={}, partLengths={}, mergedLength={}, head=\"{}\", tail=\"{}\", lastPartTail=\"{}\"",
            candidateCount,
            partsCount,
            partLengths,
            merged.length(),
            previewHead(merged, 120),
            previewTail(merged, 120),
            previewTail(lastPartText, 120)
        );
    }

    private String previewHead(String text, int maxLength) {
        String normalized = normalizePreviewText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String previewTail(String text, int maxLength) {
        String normalized = normalizePreviewText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return "..." + normalized.substring(normalized.length() - maxLength);
    }

    private String normalizePreviewText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Gemini 429 응답 body를 파싱하여 rate limit 종류를 판별
     * details[].metadata.quota_metric 값에 "daily"가 포함되면 DAILY, 그 외는 MINUTE
     * 파싱 실패 시 보수적으로 MINUTE 반환
     *
     * @param responseBody Gemini 429 응답 body
     * @return DAILY 또는 MINUTE
     */
    private RateLimitType detectRateLimitType(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            // Gemini 오류 구조: { "error": { "details": [ { "metadata": { "quota_metric": "..." } } ] } }
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
            // 파싱 실패 시 보수적으로 MINUTE 반환
            log.debug("[Gemini] 429 body 파싱 실패, MINUTE으로 처리: {}", e.getMessage());
        }
        return RateLimitType.MINUTE;
    }

    /**
     * Retry-After 헤더에서 재시도 대기 초를 추출
     * 헤더가 없거나 파싱 실패 시 기본값 60초 반환
     *
     * @param e RestClientResponseException (429)
     * @return 재시도 대기 초 (최소 1, 기본 60)
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
            log.debug("[Gemini] Retry-After 헤더 파싱 실패: {}", ex.getMessage());
        }
        return 60; // 기본 대기 시간
    }
}
