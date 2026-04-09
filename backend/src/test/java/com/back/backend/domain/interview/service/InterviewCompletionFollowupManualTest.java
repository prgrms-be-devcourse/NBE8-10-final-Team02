package com.back.backend.domain.interview.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.client.gemini.GeminiClient;
import com.back.backend.domain.ai.client.gemini.GeminiClientProperties;
import com.back.backend.domain.ai.pipeline.AiConcurrencyLimiter;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.usage.AiUsageRecorder;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

@Tag("manual")
class InterviewCompletionFollowupManualTest {

    private static final String TEMPLATE_ID = "ai.interview.followup.complete.v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```json\\s*(\\{.*?})\\s*```");

    @Test
    void manualGeminiSmokeTest() throws Exception {
        assumeTrue(isManualRunEnabled(), "manual.ai=true 또는 MANUAL_AI=true 일 때만 실행합니다.");

        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY가 필요합니다.");

        enableDebugLogs();

        String payload = loadPayloadFromMarkdown();
        AiPipeline aiPipeline = buildAiPipeline(apiKey);
        int runs = resolveRunCount();

        System.out.println("=== COMPLETE PAYLOAD ===");
        System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(OBJECT_MAPPER.readTree(payload)));

        JsonNode lastResponseNode = null;
        for (int attempt = 1; attempt <= runs; attempt++) {
            System.out.println("=== COMPLETE RUN " + attempt + " / " + runs + " ===");
            JsonNode responseNode = aiPipeline.execute(TEMPLATE_ID, payload);
            System.out.println("=== COMPLETE RESPONSE ===");
            System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(responseNode));

            assertThat(responseNode.path("followUpQuestions").isArray()).isTrue();
            assertThat(responseNode.path("followUpQuestions")).isNotEmpty();
            assertThat(isDuplicateRuntimeFollowupQuestion(payload, responseNode)).isFalse();
            lastResponseNode = responseNode;
        }

        assertNotNull(lastResponseNode);
    }

    private void enableDebugLogs() {
        setLoggerDebugLevel("com.back.backend.domain.ai.client.gemini.GeminiClient");
        setLoggerDebugLevel("com.back.backend.domain.ai.template.PromptLoader");
        setLoggerDebugLevel("com.back.backend.domain.ai.pipeline.AiPipeline");
    }

    private void setLoggerDebugLevel(String loggerName) {
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext) {
            Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
            logger.setLevel(Level.DEBUG);
        }
    }

    private boolean isManualRunEnabled() {
        if (Boolean.getBoolean("manual.ai")) {
            return true;
        }
        String envValue = System.getenv("MANUAL_AI");
        return envValue != null && Boolean.parseBoolean(envValue);
    }

    private int resolveRunCount() {
        String propertyValue = System.getProperty("manual.ai.runs");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Math.max(1, Integer.parseInt(propertyValue.trim()));
        }

        String envValue = System.getenv("MANUAL_AI_RUNS");
        if (envValue != null && !envValue.isBlank()) {
            return Math.max(1, Integer.parseInt(envValue.trim()));
        }
        return 1;
    }

    private String resolveApiKey() {
        String envValue = System.getenv("GEMINI_API_KEY");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        for (Path candidate : List.of(
                Path.of(".env.dev"),
                Path.of("..", ".env.dev")
        )) {
            String loaded = loadApiKeyFromDotEnv(candidate);
            if (loaded != null && !loaded.isBlank()) {
                return loaded;
            }
        }

        return null;
    }

    private String loadApiKeyFromDotEnv(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }

            for (String rawLine : Files.readAllLines(path)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.startsWith("GEMINI_API_KEY=")) {
                    continue;
                }

                String value = line.substring("GEMINI_API_KEY=".length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String loadPayloadFromMarkdown() throws Exception {
        Path markdownPath = resolveManualCasePath();
        String markdown = Files.readString(markdownPath);
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(markdown);
        if (!matcher.find()) {
            throw new IllegalStateException("첫 번째 ```json fenced block을 찾을 수 없습니다: " + markdownPath);
        }

        String payload = matcher.group(1).trim();
        OBJECT_MAPPER.readTree(payload);
        return payload;
    }

    private boolean isDuplicateRuntimeFollowupQuestion(String payload, JsonNode responseNode) throws Exception {
        JsonNode payloadNode = OBJECT_MAPPER.readTree(payload);
        JsonNode followUpQuestions = responseNode.path("followUpQuestions");
        if (!followUpQuestions.isArray() || followUpQuestions.isEmpty()) {
            return false;
        }

        JsonNode generated = followUpQuestions.get(0);
        int parentQuestionOrder = generated.path("parentQuestionOrder").asInt(-1);
        String generatedQuestionText = generated.path("questionText").asText();

        Set<String> equivalentQuestions = new HashSet<>();
        equivalentQuestions.add(normalizeQuestionText(generatedQuestionText));
        equivalentQuestions.add(normalizeQuestionText(toFactoryEquivalentQuestion(generatedQuestionText)));

        for (JsonNode answeredThread : payloadNode.path("answeredThreads")) {
            if (answeredThread.path("tailQuestionOrder").asInt(-1) != parentQuestionOrder) {
                continue;
            }

            JsonNode runtimeFollowupQuestion = answeredThread.path("runtimeFollowupQuestion");
            if (runtimeFollowupQuestion.isMissingNode()) {
                return false;
            }

            String runtimeQuestionText = runtimeFollowupQuestion.path("questionText").asText();
            String normalizedRuntimeQuestion = normalizeQuestionText(runtimeQuestionText);
            return equivalentQuestions.contains(normalizedRuntimeQuestion);
        }
        return false;
    }

    private String toFactoryEquivalentQuestion(String questionText) {
        return switch (questionText) {
            case "다른 대안과 비교한 기준을 설명해주실 수 있나요?" ->
                    "그 기술을 선택할 때 다른 대안과는 어떻게 비교했는지 설명해주실 수 있나요?";
            case "성과를 어떤 지표로 확인했는지 설명해주실 수 있나요?" ->
                    "그 결과를 어떤 지표나 수치로 확인했는지 설명해주실 수 있나요?";
            case "그 결과가 실제로 어떻게 개선됐는지 설명해주실 수 있나요?" ->
                    "그 결과가 실제로 어떻게 달라졌는지 조금 더 구체적으로 설명해주실 수 있나요?";
            case "그 방식을 선택한 이유를 설명해주실 수 있나요?" ->
                    "그 방식으로 접근한 이유를 조금 더 구체적으로 설명해주실 수 있나요?";
            case "문제의 근본 원인을 어떻게 파악했는지 설명해주실 수 있나요?" ->
                    "그 문제의 근본 원인을 어떻게 파악했는지 조금 더 구체적으로 설명해주실 수 있나요?";
            case "원인을 어떻게 검증했는지 설명해주실 수 있나요?" ->
                    "그 원인을 어떤 방식으로 검증했는지 조금 더 구체적으로 설명해주실 수 있나요?";
            case "그 합의가 팀에 어떤 영향을 줬는지 설명해주실 수 있나요?" ->
                    "그 협업 결과가 팀이나 프로젝트에 어떤 영향을 줬는지 설명해주실 수 있나요?";
            case "합의 기준을 어떻게 맞췄는지 설명해주실 수 있나요?" ->
                    "협업 과정에서 어떤 기준으로 합의했는지 설명해주실 수 있나요?";
            default -> questionText;
        };
    }

    private String normalizeQuestionText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private Path resolveManualCasePath() {
        for (Path candidate : List.of(
                Path.of(".local", "docs", "complete-followup-manual.md"),
                Path.of("..", ".local", "docs", "complete-followup-manual.md")
        )) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(".local/docs/complete-followup-manual.md 파일을 찾을 수 없습니다.");
    }

    private AiPipeline buildAiPipeline(String apiKey) {
        GeminiClientProperties properties = new GeminiClientProperties(
                apiKey,
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-2.5-flash",
                new GeminiClientProperties.Timeout(Duration.ofSeconds(5), Duration.ofSeconds(60)),
                new GeminiClientProperties.Retry(1)
        );

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout().connect());
        requestFactory.setReadTimeout(properties.timeout().read());

        GeminiClient geminiClient = new GeminiClient(
                RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(requestFactory)
                        .build(),
                properties
        );

        AiClientRouter router = new AiClientRouter(List.of(geminiClient), AiProvider.GEMINI, null);
        PromptTemplateRegistry templateRegistry = PromptTemplateRegistry.createDefault();
        PromptLoader promptLoader = new PromptLoader();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(OBJECT_MAPPER);
        ValidationRegistry validationRegistry = ValidationRegistry.createDefault(jsonSchemaValidator);
        AiUsageRecorder usageRecorder = mock(AiUsageRecorder.class);

        AiConcurrencyLimiter concurrencyLimiter = new AiConcurrencyLimiter(100, 60);

        return new AiPipeline(
                router,
                templateRegistry,
                validationRegistry,
                promptLoader,
                jsonSchemaValidator,
                usageRecorder,
                concurrencyLimiter,
                new com.back.backend.domain.ai.recovery.TruncatedJsonArrayRecovery(OBJECT_MAPPER)
        );
    }
}
