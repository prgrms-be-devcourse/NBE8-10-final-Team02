package com.back.backend.domain.interview.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.client.gemini.GeminiClient;
import com.back.backend.domain.ai.client.gemini.GeminiClientProperties;
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
import java.util.List;
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

        return new AiPipeline(
                router,
                templateRegistry,
                validationRegistry,
                promptLoader,
                jsonSchemaValidator,
                usageRecorder
        );
    }
}
