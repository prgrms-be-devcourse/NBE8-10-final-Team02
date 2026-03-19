package com.back.backend.ai.client.gemini;

import com.back.backend.ai.client.*;
import com.back.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Gemini API 구현
 * 공통 AiRequest를 Gemini 형식으로 변환하여 호출하고,
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
        GeminiRequest geminiRequest = GeminiRequest.from(request);

        GeminiResponse geminiResponse = doCall(geminiRequest);
        validateResponse(geminiResponse);

        return geminiResponse.toAiResponse();
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GEMINI;
    }

    private GeminiResponse doCall(GeminiRequest geminiRequest) {
        // POST /v1beta/models/{model}:generateContent?key={apiKey}
        String uri = "/models/" + properties.model() + ":generateContent?key=" + properties.apiKey();

        try {
            return restClient.post()
                .uri(uri)
                .body(geminiRequest)
                .retrieve()
                .body(GeminiResponse.class);
        } catch (Exception e) {
            log.error("[Gemini] API 호출 실패: {}", e.getMessage(), e);
            throw new AiClientException(
                AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Gemini API 호출 실패: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Gemini 응답이 유효한지 검증
     * candidates가 비어있거나 텍스트가 없으면 예외처리
     */
    private void validateResponse(GeminiResponse response) {
        if (response == null
            || response.candidates() == null
            || response.candidates().isEmpty()
            || response.candidates().getFirst().content() == null
            || response.candidates().getFirst().content().parts() == null
            || response.candidates().getFirst().content().parts().isEmpty()) {
            log.warn("[Gemini] 빈 응답 수신");
            throw new AiClientException(
                AiProvider.GEMINI,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                "Gemini API가 빈 응답을 반환했습니다"
            );
        }
    }
}
