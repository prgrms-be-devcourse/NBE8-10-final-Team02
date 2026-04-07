package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeEvaluateValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private JsonSchemaValidator jsonSchemaValidator;
    private PracticeEvaluateCsValidator csValidator;
    private PracticeEvaluateBehavioralValidator behavioralValidator;

    @BeforeEach
    void setUp() {
        jsonSchemaValidator = new JsonSchemaValidator(OBJECT_MAPPER);
        csValidator = new PracticeEvaluateCsValidator(jsonSchemaValidator);
        behavioralValidator = new PracticeEvaluateBehavioralValidator(jsonSchemaValidator);
    }

    @Test
    void csValidator_validResponse_passes() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "score": 80,
                  "feedback": "정확한 설명이다. 깊이가 부족하다. 원리를 추가하라.",
                  "modelAnswer": "TCP 3-way handshake는 SYN-ACK 과정입니다.",
                  "tagNames": ["기술 깊이 부족"]
                }
                """);

        ValidationResult result = csValidator.validate(node);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void csValidator_blankFeedback_fails() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "score": 80,
                  "feedback": "   ",
                  "modelAnswer": "모범답안입니다.",
                  "tagNames": []
                }
                """);

        ValidationResult result = csValidator.validate(node);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("feedback"));
    }

    @Test
    void csValidator_blankModelAnswer_fails() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "score": 80,
                  "feedback": "좋은 답변입니다.",
                  "modelAnswer": "  ",
                  "tagNames": []
                }
                """);

        ValidationResult result = csValidator.validate(node);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("modelAnswer"));
    }

    @Test
    void csValidator_scoreOutOfRange_failsSchema() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "score": 150,
                  "feedback": "좋은 답변입니다.",
                  "modelAnswer": "모범답안입니다.",
                  "tagNames": []
                }
                """);

        ValidationResult result = csValidator.validate(node);
        assertThat(result.valid()).isFalse();
    }

    @Test
    void behavioralValidator_validResponse_passes() throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree("""
                {
                  "score": 65,
                  "feedback": "상황 설명이 구체적이다. STAR 구조가 미비하다. 행동과 결과를 분리하여 서술하라.",
                  "modelAnswer": "이런 구조로 답하면 좋습니다: 상황 → 과제 → 행동 → 결과",
                  "tagNames": ["답변 구조 미흡"]
                }
                """);

        ValidationResult result = behavioralValidator.validate(node);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void behavioralValidator_templateId_isCorrect() {
        assertThat(csValidator.getTemplateId()).isEqualTo("ai.practice.evaluate.cs.v1");
        assertThat(behavioralValidator.getTemplateId()).isEqualTo("ai.practice.evaluate.behavioral.v1");
    }
}
