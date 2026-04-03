package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewQuestionsPayloadBuilderTest {

    private final InterviewQuestionsPayloadBuilder builder = new InterviewQuestionsPayloadBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JOB_ROLE = "백엔드 개발자";
    private static final String COMPANY_NAME = "카카오";
    private static final int QUESTION_COUNT = 5;
    private static final String DIFFICULTY_LEVEL = "medium";
    private static final List<String> QUESTION_TYPES = List.of("technical_cs", "project", "behavioral");

    private String buildDefault() {
        return builder.build(
            JOB_ROLE, COMPANY_NAME, List.of(), List.of(),
            QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
        );
    }

    @Nested
    @DisplayName("null 파라미터 방어")
    class NullGuard {

        @Test
        @DisplayName("jobRole이 null이면 NullPointerException을 던진다")
        void null_jobRole() {
            assertThatThrownBy(() -> builder.build(
                null, COMPANY_NAME, List.of(), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobRole");
        }

        @Test
        @DisplayName("selfIntroQnAs가 null이면 NullPointerException을 던진다")
        void null_selfIntroQnAs() {
            assertThatThrownBy(() -> builder.build(
                JOB_ROLE, COMPANY_NAME, null, List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("selfIntroQnAs");
        }

        @Test
        @DisplayName("documentTexts가 null이면 NullPointerException을 던진다")
        void null_documentTexts() {
            assertThatThrownBy(() -> builder.build(
                JOB_ROLE, COMPANY_NAME, List.of(), null,
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("documentTexts");
        }

        @Test
        @DisplayName("difficultyLevel이 null이면 NullPointerException을 던진다")
        void null_difficultyLevel() {
            assertThatThrownBy(() -> builder.build(
                JOB_ROLE, COMPANY_NAME, List.of(), List.of(),
                QUESTION_COUNT, null, QUESTION_TYPES, List.of()
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("difficultyLevel");
        }

        @Test
        @DisplayName("questionTypes가 null이면 NullPointerException을 던진다")
        void null_questionTypes() {
            assertThatThrownBy(() -> builder.build(
                JOB_ROLE, COMPANY_NAME, List.of(), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, null, List.of()
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("questionTypes");
        }
    }

    @Nested
    @DisplayName("기본 필드 생성")
    class BasicFields {

        @Test
        @DisplayName("jobRole, companyName, questionCount, difficultyLevel이 payload에 포함된다")
        void basic_fields_included() throws Exception {
            JsonNode root = objectMapper.readTree(buildDefault());

            assertThat(root.get("jobRole").asText()).isEqualTo(JOB_ROLE);
            assertThat(root.get("companyName").asText()).isEqualTo(COMPANY_NAME);
            assertThat(root.get("preferredQuestionCount").asInt()).isEqualTo(QUESTION_COUNT);
            assertThat(root.get("difficultyLevel").asText()).isEqualTo(DIFFICULTY_LEVEL);
        }

        @Test
        @DisplayName("companyName이 null이면 payload에 포함되지 않는다")
        void null_companyName_excluded() throws Exception {
            String payload = builder.build(
                JOB_ROLE, null, List.of(), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.has("companyName")).isFalse();
        }

        @Test
        @DisplayName("companyName이 공백이면 payload에 포함되지 않는다")
        void blank_companyName_excluded() throws Exception {
            String payload = builder.build(
                JOB_ROLE, "  ", List.of(), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.has("companyName")).isFalse();
        }
    }

    @Nested
    @DisplayName("questionTypes 생성")
    class QuestionTypes {

        @Test
        @DisplayName("질문 유형이 올바르게 배열로 직렬화된다")
        void question_types_serialized() throws Exception {
            JsonNode types = objectMapper.readTree(buildDefault()).get("questionTypes");

            List<String> actual = StreamSupport.stream(types.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
            assertThat(actual).containsExactly("technical_cs", "project", "behavioral");
        }

        @Test
        @DisplayName("빈 유형 목록이면 questionTypes가 빈 배열이다")
        void empty_question_types() throws Exception {
            String payload = builder.build(
                JOB_ROLE, null, List.of(), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, List.of(), List.of()
            );
            JsonNode types = objectMapper.readTree(payload).get("questionTypes");

            assertThat(types.isArray()).isTrue();
            assertThat(types.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("selfIntroQnA 생성")
    class SelfIntroQnA {

        @Test
        @DisplayName("자소서 Q&A가 올바르게 직렬화된다")
        void qna_serialized_correctly() throws Exception {
            var qna = new InterviewQuestionsPayloadBuilder.SelfIntroQnA(
                1, "지원 동기를 작성해주세요.", "저는 카카오에 지원하게 된 동기는..."
            );
            String payload = builder.build(
                JOB_ROLE, null, List.of(qna), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode node = objectMapper.readTree(payload).get("selfIntroQnA").get(0);

            assertThat(node.get("questionOrder").asInt()).isEqualTo(1);
            assertThat(node.get("questionText").asText()).isEqualTo("지원 동기를 작성해주세요.");
            assertThat(node.get("generatedAnswer").asText()).isEqualTo("저는 카카오에 지원하게 된 동기는...");
        }

        @Test
        @DisplayName("generatedAnswer가 null이면 해당 필드가 포함되지 않는다")
        void null_generatedAnswer_excluded() throws Exception {
            var qna = new InterviewQuestionsPayloadBuilder.SelfIntroQnA(
                1, "지원 동기를 작성해주세요.", null
            );
            String payload = builder.build(
                JOB_ROLE, null, List.of(qna), List.of(),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode node = objectMapper.readTree(payload).get("selfIntroQnA").get(0);

            assertThat(node.has("generatedAnswer")).isFalse();
        }

        @Test
        @DisplayName("자소서 Q&A가 없으면 selfIntroQnA가 빈 배열이다")
        void empty_qna() throws Exception {
            JsonNode qna = objectMapper.readTree(buildDefault()).get("selfIntroQnA");

            assertThat(qna.isArray()).isTrue();
            assertThat(qna.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("portfolioEvidence 생성")
    class PortfolioEvidence {

        @Test
        @DisplayName("문서가 독립 증거 항목으로 변환된다")
        void document_becomes_evidence() throws Exception {
            String payload = builder.build(
                JOB_ROLE, null, List.of(), List.of("Spring Boot 프로젝트 경험"),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode ev = objectMapper.readTree(payload).get("portfolioEvidence").get(0);

            assertThat(ev.get("projectKey").asText()).isEqualTo("doc_1");
            assertThat(ev.get("summary").asText()).isEqualTo("Spring Boot 프로젝트 경험");
        }

        @Test
        @DisplayName("여러 문서는 doc_1, doc_2 순으로 key가 부여된다")
        void multiple_documents_indexed() throws Exception {
            String payload = builder.build(
                JOB_ROLE, null, List.of(), List.of("문서1", "문서2"),
                QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES, List.of()
            );
            JsonNode evidence = objectMapper.readTree(payload).get("portfolioEvidence");

            assertThat(evidence.get(0).get("projectKey").asText()).isEqualTo("doc_1");
            assertThat(evidence.get(1).get("projectKey").asText()).isEqualTo("doc_2");
        }

        @Test
        @DisplayName("문서가 없으면 portfolioEvidence가 빈 배열이다")
        void empty_evidence() throws Exception {
            JsonNode evidence = objectMapper.readTree(buildDefault()).get("portfolioEvidence");

            assertThat(evidence.isArray()).isTrue();
            assertThat(evidence.isEmpty()).isTrue();
        }
    }
}
