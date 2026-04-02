package com.back.backend.domain.ai.validation;

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
            // project 필드 없음
            JsonNode node = objectMapper.readTree("""
                {
                  "qualityFlags": []
                }
                """);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
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

        @Test
        @DisplayName("evidenceBullets가 5개를 초과하면 실패한다")
        void evidenceBullets_exceed_max() throws Exception {
            String json = """
            {
              "project": {
                "projectKey": "test-repo",
                "projectName": "테스트 프로젝트",
                "summary": "요약입니다.",
                "role": "백엔드 개발",
                "stack": ["Java 17"],
                "signals": ["OAuth2"],
                "strengths": ["강점"],
                "risks": ["약점"],
                "sourceRefs": ["repo:101"],
                "qualityFlags": [],
                "challenges": [
                  { "id": "c1", "what": "문제", "how": "해결", "learning": "배움" }
                ],
                "techDecisions": [
                  { "decision": "결정", "reason": "이유", "tradeOff": null }
                ],
                "evidenceBullets": [
                  { "fact": "총알 1", "challengeRef": null },
                  { "fact": "총알 2", "challengeRef": null },
                  { "fact": "총알 3", "challengeRef": null },
                  { "fact": "총알 4", "challengeRef": null },
                  { "fact": "총알 5", "challengeRef": null },
                  { "fact": "총알 6", "challengeRef": null }
                ]
              }
            }
            """;
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }

        @Test
        @DisplayName("challenges가 3개를 초과하면 실패한다")
        void challenges_exceed_max() throws Exception {
            String extraChallenge = "{\"id\": \"cx\", \"what\": \"w\", \"how\": \"h\", \"learning\": \"l\"}";
            String json = validResponse().replace(
                "\"challenges\": []",
                "\"challenges\": [" + extraChallenge + "," + extraChallenge + "," +
                extraChallenge + "," + extraChallenge + "]"
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }

        @Test
        @DisplayName("evidenceBullets의 fact가 빈 문자열이면 실패한다")
        void evidence_fact_empty_string() throws Exception {
            String json = validResponse().replace(
                "\"fact\": \"OAuth2 로그인 구현\"",
                "\"fact\": \"\""
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
              "project": {
                "projectKey": "ai-interview-platform",
                "projectName": "AI Interview Platform",
                "summary": "포트폴리오 기반 면접 준비 플랫폼",
                "role": "백엔드 개발 / 인증 도메인 담당",
                "stack": ["Java 17", "Spring Boot 3", "Redis"],
                "signals": ["Spring Boot", "OAuth2"],
                "evidenceBullets": [
                  {"fact": "OAuth2 로그인 구현", "challengeRef": null}
                ],
                "challenges": [],
                "techDecisions": [],
                "strengths": ["백엔드 설계 경험"],
                "risks": ["정량 근거 부족"],
                "sourceRefs": ["repo:101"],
                "qualityFlags": []
              }
            }
            """;
    }
}
