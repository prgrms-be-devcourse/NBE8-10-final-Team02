package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSummaryValidatorTest {

    private InterviewSummaryValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new InterviewSummaryValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.interview.summary.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.interview.summary.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("정상 응답은 검증을 통과한다")
        void valid_response() {
            JsonNode node = buildResponse(
                "전반적으로 준비가 잘 되어있습니다.",
                List.of("논리적 사고"),
                List.of("구체적 근거 부족"),
                List.of("프로젝트 경험 정리")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("필수 필드 누락 시 실패한다")
        void missing_required_field() {
            ObjectNode node = objectMapper.createObjectNode();
            node.putArray("qualityFlags");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("strengths가 4개 이상이면 실패한다")
        void strengths_exceed_max() {
            JsonNode node = buildResponse(
                "요약입니다.",
                List.of("강점1", "강점2", "강점3", "강점4"),
                List.of("약점1"),
                List.of("액션1")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("strengths"));
        }

        @Test
        @DisplayName("weaknesses가 비어있으면 실패한다")
        void weaknesses_empty() {
            JsonNode node = buildResponse(
                "요약입니다.",
                List.of("강점1"),
                List.of(),
                List.of("액션1")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("weaknesses"));
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("shortSummary가 공백 문자열이면 실패한다")
        void blank_shortSummary() {
            JsonNode node = buildResponse(
                "   ",
                List.of("강점1"),
                List.of("약점1"),
                List.of("액션1")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("shortSummary가 비어있습니다"));
        }

        @Test
        @DisplayName("strengths에 중복 항목이 있으면 실패한다")
        void duplicate_strengths() {
            JsonNode node = buildResponse(
                "요약입니다.",
                List.of("논리적 사고", "논리적 사고"),
                List.of("약점1"),
                List.of("액션1")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                e.contains("strengths") && e.contains("중복")
            );
        }

        @Test
        @DisplayName("nextActions에 공백 항목이 있으면 실패한다")
        void blank_item_in_nextActions() {
            JsonNode node = buildResponse(
                "요약입니다.",
                List.of("강점1"),
                List.of("약점1"),
                List.of("   ")
            );

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                e.contains("nextActions") && e.contains("공백")
            );
        }
    }

    private JsonNode buildResponse(String shortSummary, List<String> strengths,
                                   List<String> weaknesses, List<String> nextActions) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("shortSummary", shortSummary);
        addStringArray(root, "strengths", strengths);
        addStringArray(root, "weaknesses", weaknesses);
        addStringArray(root, "nextActions", nextActions);
        root.putArray("qualityFlags");
        return root;
    }

    private void addStringArray(ObjectNode root, String fieldName, List<String> items) {
        ArrayNode array = root.putArray(fieldName);
        items.forEach(array::add);
    }
}
