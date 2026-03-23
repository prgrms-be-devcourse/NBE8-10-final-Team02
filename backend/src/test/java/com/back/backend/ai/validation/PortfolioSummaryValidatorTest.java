package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioSummaryValidatorTest {

    private PortfolioSummaryValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new PortfolioSummaryValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.portfolio.summary.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.portfolio.summary.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("정상 응답은 검증을 통과한다")
        void valid_response() throws Exception {
            JsonNode node = objectMapper.readTree(validResponse());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        @DisplayName("필수 필드 누락 시 실패한다")
        void missing_required_field() throws Exception {
            // projects 필드 없음
            JsonNode node = objectMapper.readTree("""
                {
                  "globalStrengths": [],
                  "globalRisks": [],
                  "qualityFlags": []
                }
                """);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("confidence가 허용 enum이 아니면 실패한다")
        void invalid_confidence_enum() throws Exception {
            String json = validResponse().replace("\"high\"", "\"very_high\"");
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }

        @Test
        @DisplayName("스키마에 없는 임의 필드가 있으면 실패한다")
        void additional_properties_rejected() throws Exception {
            String json = validResponse().replace(
                "\"qualityFlags\": []",
                "\"qualityFlags\": [], \"extraField\": \"unexpected\""
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }

        @Test
        @DisplayName("signals가 6개를 초과하면 실패한다")
        void signals_exceed_max() throws Exception {
            String json = validResponse().replace(
                "\"signals\": [\"Spring Boot\", \"OAuth2\"]",
                "\"signals\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]"
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("projectKey가 중복되면 실패한다")
        void duplicate_projectKey() throws Exception {
            JsonNode node = objectMapper.readTree(duplicateProjectKeyResponse());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("projectKey 중복"));
        }

        @Test
        @DisplayName("sourceRefs가 비어있으면 경고와 함께 통과한다")
        void empty_sourceRefs_warning() throws Exception {
            String json = validResponse().replace(
                "\"sourceRefs\": [\"repo:101\"]",
                "\"sourceRefs\": []"
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("sourceRefs가 비어있습니다"));
        }
    }

    private String validResponse() {
        return """
            {
              "projects": [
                {
                  "projectKey": "project_1",
                  "projectName": "AI Interview Platform",
                  "summary": "포트폴리오 기반 면접 준비 플랫폼",
                  "signals": ["Spring Boot", "OAuth2"],
                  "evidenceBullets": ["OAuth2 로그인 구현"],
                  "confidence": "high",
                  "sourceRefs": ["repo:101"],
                  "qualityFlags": []
                }
              ],
              "globalStrengths": ["백엔드 설계 경험"],
              "globalRisks": ["정량 근거 부족"],
              "qualityFlags": []
            }
            """;
    }

    private String duplicateProjectKeyResponse() {
        return """
            {
              "projects": [
                {
                  "projectKey": "project_1",
                  "projectName": "Project A",
                  "summary": "요약 A",
                  "signals": ["Spring Boot"],
                  "evidenceBullets": ["구현 A"],
                  "confidence": "high",
                  "sourceRefs": ["repo:101"],
                  "qualityFlags": []
                },
                {
                  "projectKey": "project_1",
                  "projectName": "Project B",
                  "summary": "요약 B",
                  "signals": ["React"],
                  "evidenceBullets": ["구현 B"],
                  "confidence": "medium",
                  "sourceRefs": ["repo:102"],
                  "qualityFlags": []
                }
              ],
              "globalStrengths": ["경험"],
              "globalRisks": ["부족"],
              "qualityFlags": []
            }
            """;
    }
}
