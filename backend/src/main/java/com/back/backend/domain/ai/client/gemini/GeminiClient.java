package com.back.backend.domain.ai.client.gemini;

import com.back.backend.domain.ai.client.*;
import com.back.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini API 구현체
 * 공통 AiRequest를 Gemini 형식으로 변환하여 호출하고
 * 응답을 공통 AiResponse로 변환하여 반환
 */
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

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
            // 429 할당량 초과 — 별도 로그로 명확히 표시
            if (e.getStatusCode().value() == 429) {
                log.warn("[Gemini] API 호출 횟수 초과. 무료 티어 일일 한도에 도달했습니다. body={}", e.getResponseBodyAsString());
                throw new AiClientException(
                    AiProvider.GEMINI,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    "AI API 호출 횟수가 부족합니다. 잠시 후 다시 시도해주세요.",
                    e
                );
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
}
