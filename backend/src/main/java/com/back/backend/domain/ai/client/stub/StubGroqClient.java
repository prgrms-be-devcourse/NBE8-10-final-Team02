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
 * Groq API Stub — load-test profile 전용.
 *
 * Groq Llama 3.3 70B Versatile 기준 응답 지연을 재현.
 * LPU 특성상 Gemini보다 프리필과 출력 모두 빠르다.
 */
public class StubGroqClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(StubGroqClient.class);
    private static final LatencyModel LATENCY = LatencyModel.groqLlama3();

    private final ConcurrentHashMap<String, String> fixtureCache = new ConcurrentHashMap<>();

    @Override
    public AiResponse call(AiRequest request) {
        String content = resolveFixture(request.developerPrompt());
        long delayMs   = LATENCY.computeDelayMs(request, content);

        log.debug("[STUB-GROQ] delay={}ms", delayMs);
        simulateLatency(delayMs);

        return buildResponse(request, content);
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.GROQ;
    }

    // ── 내부 구현 (StubGeminiClient와 동일 구조) ──────────────────────────

    private void simulateLatency(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private AiResponse buildResponse(AiRequest request, String content) {
        int out = (int) Math.ceil(content.length() / 3.5);
        int in  = estimateInputTokens(request);
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

    private String detectTemplateId(String developerPrompt) {
        if (developerPrompt == null) return "default";
        if (developerPrompt.contains("answerText를 생성한다"))      return "self-intro-generate";
        if (developerPrompt.contains("preferredQuestionCount"))     return "interview-questions-generate";
        if (developerPrompt.contains("relevance") && developerPrompt.contains("평가 축")) return "interview-evaluate";
        if (developerPrompt.contains("isSkipped"))                  return "interview-followup-generate";
        if (developerPrompt.contains("strengths") && developerPrompt.contains("nextActions")) return "interview-summary";
        if (developerPrompt.contains("복수의 repository"))           return "portfolio-summary-batch";
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
            log.warn("[STUB-GROQ] fixture 없음: {} → default 사용", path);
            return loadFixture("default");
        } catch (IOException e) {
            throw new UncheckedIOException("stub fixture 로드 실패: " + path, e);
        }
    }
}
