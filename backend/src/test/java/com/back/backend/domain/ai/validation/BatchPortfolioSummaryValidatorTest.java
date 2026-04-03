package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchPortfolioSummaryValidatorTest {

    private BatchPortfolioSummaryValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new BatchPortfolioSummaryValidator();
    }

    @Test
    @DisplayName("templateId가 ai.portfolio.summary.batch.v1이다")
    void getTemplateId_returnsBatchTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.portfolio.summary.batch.v1");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 배열 구조 검증
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("배열 구조 검증")
    class ArrayStructureValidation {

        @Test
        @DisplayName("정상 배치 응답은 검증을 통과한다")
        void valid_batchResponse_passes() throws Exception {
            JsonNode node = objectMapper.readTree(validBatchResponse());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("단일 객체 응답은 경고와 함께 통과한다 (모델이 배열 대신 객체를 반환하는 경우 허용)")
        void singleObject_passesWithWarning() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    { "repoId": "my-repo", "project": %s }
                    """.formatted(validProject("my-repo")));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("단일 객체"));
        }

        @Test
        @DisplayName("배열도 객체도 아닌 타입이면 실패한다")
        void nonArrayNonObject_fails() throws Exception {
            JsonNode node = objectMapper.readTree("\"invalid string response\"");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("배열"));
        }

        @Test
        @DisplayName("빈 배열이면 실패한다")
        void emptyArray_fails() throws Exception {
            JsonNode node = objectMapper.readTree("[]");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("비어있"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 원소 필드 검증
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("원소 필드 검증")
    class ElementFieldValidation {

        @Test
        @DisplayName("repoId가 없으면 실패한다")
        void missingRepoId_fails() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    [
                      {
                        "project": %s
                      }
                    ]
                    """.formatted(validProject("my-repo")));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("repoId"));
        }

        @Test
        @DisplayName("repoId가 blank이면 실패한다")
        void blankRepoId_fails() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    [
                      {
                        "repoId": "   ",
                        "project": %s
                      }
                    ]
                    """.formatted(validProject("my-repo")));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("repoId"));
        }

        @Test
        @DisplayName("project 필드가 없으면 실패한다")
        void missingProject_fails() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    [
                      { "repoId": "my-repo" }
                    ]
                    """);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("project"));
        }

        @Test
        @DisplayName("repoId가 중복되면 실패한다")
        void duplicateRepoId_fails() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    [
                      { "repoId": "same-repo", "project": %s },
                      { "repoId": "same-repo", "project": %s }
                    ]
                    """.formatted(validProject("same-repo"), validProject("same-repo")));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("중복") && e.contains("same-repo"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // project 필수 필드 검증
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("project 필수 필드 검증")
    class ProjectFieldValidation {

        @Test
        @DisplayName("필수 필드(summary) 누락 시 실패한다")
        void missingSummaryField_fails() throws Exception {
            // summary 필드를 제거
            String projectWithoutSummary = validProject("my-repo")
                    .replace("\"summary\": \"테스트 프로젝트\",", "");
            JsonNode node = objectMapper.readTree("""
                    [{ "repoId": "my-repo", "project": %s }]
                    """.formatted(projectWithoutSummary));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("summary"));
        }

        @Test
        @DisplayName("필수 필드(stack) 누락 시 실패한다")
        void missingStackField_fails() throws Exception {
            String projectWithoutStack = validProject("my-repo")
                    .replace("\"stack\": [\"Java\"],", "");
            JsonNode node = objectMapper.readTree("""
                    [{ "repoId": "my-repo", "project": %s }]
                    """.formatted(projectWithoutStack));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("stack"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // cross-field 검증 (경고)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("sourceRefs가 비어있으면 경고와 함께 통과한다")
        void emptySourceRefs_warnsButPasses() throws Exception {
            String projectEmptySourceRefs = validProject("my-repo")
                    .replace("\"sourceRefs\": [\"repo:101\"]", "\"sourceRefs\": []");
            JsonNode node = objectMapper.readTree("""
                    [{ "repoId": "my-repo", "project": %s }]
                    """.formatted(projectEmptySourceRefs));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("sourceRefs"));
        }

        @Test
        @DisplayName("여러 repo가 모두 유효하면 통과한다")
        void multipleValidRepos_passes() throws Exception {
            JsonNode node = objectMapper.readTree("""
                    [
                      { "repoId": "repo-a", "project": %s },
                      { "repoId": "repo-b", "project": %s }
                    ]
                    """.formatted(validProject("repo-a"), validProject("repo-b")));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("challenges가 있는데 evidenceBullets가 비어있으면 경고가 발생한다")
        void challengesWithoutEvidence_warns() throws Exception {
            String projectWithChallengeNoEvidence = validProject("my-repo")
                    .replace(
                        "\"evidenceBullets\": [{\"fact\": \"기능 구현\", \"challengeRef\": null}]",
                        "\"evidenceBullets\": []"
                    )
                    .replace(
                        "\"challenges\": []",
                        "\"challenges\": [{\"id\": \"c1\", \"what\": \"w\", \"how\": \"h\", \"learning\": \"l\"}]"
                    );
            JsonNode node = objectMapper.readTree("""
                    [{ "repoId": "my-repo", "project": %s }]
                    """.formatted(projectWithChallengeNoEvidence));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("evidenceBullets"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private String validBatchResponse() {
        return """
                [
                  { "repoId": "repo-a", "project": %s },
                  { "repoId": "repo-b", "project": %s }
                ]
                """.formatted(validProject("repo-a"), validProject("repo-b"));
    }

    private String validProject(String repoId) {
        return """
                {
                  "projectKey": "%s",
                  "projectName": "Test Project",
                  "summary": "테스트 프로젝트",
                  "role": "백엔드 개발자",
                  "stack": ["Java"],
                  "signals": ["Spring Boot"],
                  "evidenceBullets": [{"fact": "기능 구현", "challengeRef": null}],
                  "challenges": [],
                  "techDecisions": [],
                  "strengths": ["설계 경험"],
                  "risks": ["테스트 부족"],
                  "sourceRefs": ["repo:101"]
                }
                """.formatted(repoId);
    }
}
