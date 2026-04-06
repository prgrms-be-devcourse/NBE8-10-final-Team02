package com.back.backend.domain.ai.client.stub;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.client.AiRequest;
import com.back.backend.domain.ai.client.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gemini API Stub — load-test profile 전용.
 *
 * 실제 외부 API 호출 없이 classpath stubs/*.json fixture를 반환.
 * Gemini 2.5 Flash 기준 응답 지연(입력/출력 토큰 기반)을 Thread.sleep()으로 재현해
 * 플랫폼 스레드 점유 상태를 모사한다.
 */
public class StubGeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(StubGeminiClient.class);
    private static final LatencyModel LATENCY = LatencyModel.geminiFlash();

    private final ConcurrentHashMap<String, String> fixtureCache = new ConcurrentHashMap<>();

    @Override
    public AiResponse call(AiRequest request) {
        String content = resolveFixture(request.developerPrompt());
        long delayMs   = LATENCY.computeDelayMs(request, content);

        log.debug("[STUB-GEMINI] delay={}ms", delayMs);
        simulateLatency(delayMs);

        return buildResponse(request, content);
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GEMINI;
    }

    // ── 내부 구현 ──────────────────────────────────────────────────────────

    private void simulateLatency(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private AiResponse buildResponse(AiRequest request, String content) {
        int out   = (int) Math.ceil(content.length() / 3.5);
        int in    = estimateInputTokens(request);
        return new AiResponse(content, new AiResponse.TokenUsage(in, out, in + out));
    }

    private int estimateInputTokens(AiRequest request) {
        int chars = 0;
        if (request.systemPrompt()    != null) chars += request.systemPrompt().length();
        if (request.developerPrompt() != null) chars += request.developerPrompt().length();
        if (request.userMessage()     != null) chars += request.userMessage().length();
        return (int) Math.ceil(chars / 3.5);
    }

    private String resolveFixture(String developerPrompt) {
        String templateId = detectTemplateId(developerPrompt);
        return fixtureCache.computeIfAbsent(templateId, this::loadFixture);
    }

    /**
     * developerPrompt의 고유 문구로 템플릿을 판별.
     * systemPrompt는 모든 템플릿에서 common-system.txt로 동일하므로 사용 불가.
     */
    private String detectTemplateId(String developerPrompt) {
        if (developerPrompt == null) return "default";
        if (developerPrompt.contains("answerText를 생성한다"))      return "self-intro-generate";
        if (developerPrompt.contains("preferredQuestionCount"))     return "interview-questions-generate";
        if (developerPrompt.contains("relevance") && developerPrompt.contains("평가 축")) return "interview-evaluate";
        if (developerPrompt.contains("isSkipped"))                  return "interview-followup-generate";
        if (developerPrompt.contains("strengths") && developerPrompt.contains("nextActions")) return "interview-summary";
        if (developerPrompt.contains("evidence pack"))              return "portfolio-summary";
        return "default";
    }

    private String loadFixture(String templateId) {
        String path = "stubs/" + templateId + ".json";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
            log.warn("[STUB-GEMINI] fixture 없음: {} → default 사용", path);
            return loadFixture("default");
        } catch (IOException e) {
            throw new UncheckedIOException("stub fixture 로드 실패: " + path, e);
        }
    }
}
