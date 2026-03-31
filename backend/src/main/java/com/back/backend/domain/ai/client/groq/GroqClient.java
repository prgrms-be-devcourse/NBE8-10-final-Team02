package com.back.backend.domain.ai.client.groq;

import com.back.backend.domain.ai.client.*;
import com.back.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Groq API 구현체 (OpenAI Chat Completions 호환)
 * 공통 AiRequest를 OpenAI 형식으로 변환하여 Groq에 호출하고 응답을 공통 AiResponse로 변환하여 반환
 * <p>
 * Gemini 할당량 초과 시 fallback으로 사용됨
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
            // 429 할당량 초과
            if (e.getStatusCode().value() == 429) {
                log.warn("[Groq] API 호출 횟수 초과 (429 TOO_MANY_REQUESTS). body={}", e.getResponseBodyAsString());
                throw new AiClientException(
                    AiProvider.GROQ,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    "Groq API 호출 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.",
                    e
                );
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
}
