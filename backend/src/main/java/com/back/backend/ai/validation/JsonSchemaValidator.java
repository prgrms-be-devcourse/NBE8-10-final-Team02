package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON Schema 기반 공통 검증
 * classpath의 .schema.json 파일을 로딩하여 AI 응답을 검증
 * <p>
 * schema는 최초 1회만 로딩하고 캐싱한다 — 런타임에 변경되지 않음
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);
    private static final String SCHEMA_BASE_PATH = "/ai/schema/";

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * AI 응답 문자열을 JSON으로 파싱
     */
    public ParseResult parse(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node == null || !node.isObject()) {
                return ParseResult.failure("최상위 JSON object가 아닙니다");
            }
            return ParseResult.success(node);
        } catch (Exception e) {
            log.warn("AI 응답 JSON 파싱 실패: {}", e.getMessage());
            return ParseResult.failure("JSON 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * 파싱된 JSON을 schema 파일로 검증
     * schema는 최초 호출 시 1회 로딩 후 캐싱
     */
    public ValidationResult validateSchema(JsonNode responseNode, String schemaFile) {
        try {
            JsonSchema schema = schemaCache.computeIfAbsent(schemaFile, this::loadSchema);
            Set<ValidationMessage> errors = schema.validate(responseNode);

            if (errors.isEmpty()) {
                return ValidationResult.success();
            }

            List<String> errorMessages = errors.stream()
                .map(ValidationMessage::getMessage)
                .toList();

            log.warn("AI 응답 schema 검증 실패: schemaFile={}, errors={}", schemaFile, errorMessages);
            return ValidationResult.failure(errorMessages);

        } catch (Exception e) {
            log.error("schema 로딩 실패: schemaFile={}, error={}", schemaFile, e.getMessage(), e);
            return ValidationResult.failure("schema 로딩 실패: " + schemaFile);
        }
    }

    private JsonSchema loadSchema(String schemaFile) {
        String path = SCHEMA_BASE_PATH + schemaFile;

        try (InputStream schemaStream = getClass().getResourceAsStream(path)) {
            if (schemaStream == null) {
                throw new IllegalArgumentException("schema 파일을 찾을 수 없습니다: " + path);
            }

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            return factory.getSchema(schemaStream);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("schema 로딩 중 오류: " + path, e);
        }
    }

    /**
     * JSON 파싱 결과.
     */
    public sealed interface ParseResult {
        record Success(JsonNode node) implements ParseResult {
        }

        record Failure(String error) implements ParseResult {
        }

        static ParseResult success(JsonNode node) {
            return new Success(node);
        }

        static ParseResult failure(String error) {
            return new Failure(error);
        }
    }
}
