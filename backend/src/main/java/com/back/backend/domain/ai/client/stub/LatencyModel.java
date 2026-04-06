package com.back.backend.domain.ai.client.stub;

import com.back.backend.domain.ai.client.AiRequest;

/**
 * LLM 응답 시간 모델.
 *
 * 공식: latency = networkRttMs
 *              + (inputTokens  / prefillToksPerSec  * 1000)
 *              + (outputTokens / outputToksPerSec * 1000)
 *
 * 입력 토큰이 클수록 TTFT(Time To First Token)가 길어지는 실제 LLM 동작을 반영.
 * ±20% Jitter를 추가해 결정론적 지연이 아닌 현실적인 분산을 재현.
 */
record LatencyModel(long networkRttMs, double prefillToksPerSec, double outputToksPerSec) {

    long computeDelayMs(AiRequest request, String fixtureContent) {
        int inputTokens  = estimateTokens(totalInputChars(request));
        int outputTokens = estimateTokens(fixtureContent.length());

        long prefillMs    = Math.round(inputTokens  / prefillToksPerSec  * 1_000);
        long generationMs = Math.round(outputTokens / outputToksPerSec * 1_000);
        long baseMs = networkRttMs + prefillMs + generationMs;

        return applyJitter(baseMs);
    }

    /**
     * 한국어·JSON 혼합 텍스트 토큰 수 근사.
     * 영어 기준 chars/4 보다 짧게 chars/3.5 적용.
     */
    private int estimateTokens(int charCount) {
        return Math.max(1, (int) Math.ceil(charCount / 3.5));
    }

    private int totalInputChars(AiRequest request) {
        int chars = 0;
        if (request.systemPrompt()    != null) chars += request.systemPrompt().length();
        if (request.developerPrompt() != null) chars += request.developerPrompt().length();
        if (request.userMessage()     != null) chars += request.userMessage().length();
        return chars;
    }

    /** ±20% 균등 분포 jitter */
    private long applyJitter(long baseMs) {
        double factor = 0.8 + Math.random() * 0.4;
        return Math.round(baseMs * factor);
    }

    /**
     * Gemini 2.5 Flash — sync 비스트리밍 기준 p50.
     * 네트워크 왕복 100ms, 프리필 1,500 tok/s, 출력 80 tok/s.
     */
    static LatencyModel geminiFlash() {
        return new LatencyModel(100L, 1_500.0, 80.0);
    }

    /**
     * Groq Llama 3.3 70B Versatile — LPU 기반 p50.
     * 네트워크 왕복 100ms, 프리필 4,000 tok/s, 출력 240 tok/s.
     */
    static LatencyModel groqLlama3() {
        return new LatencyModel(100L, 4_000.0, 240.0);
    }
}
