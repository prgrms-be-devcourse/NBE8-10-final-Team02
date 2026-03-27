package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewQuestionPayloadBuilderTest {

    private final InterviewQuestionPayloadBuilder builder = new InterviewQuestionPayloadBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("null 파라미터 방어")
    class NullGuard {

        @Test
        @DisplayName("jobRole이 null이면 NullPointerException을 던진다")
        void null_jobRole() {
            assertThatThrownBy(() -> builder.build(null, "회사", 5, "medium", List.of(), List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("jobRole");
        }

        @Test
        @DisplayName("difficultyLevel이 null이면 NullPointerException을 던진다")
        void null_difficultyLevel() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", 5, null, List.of(), List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("difficultyLevel");
        }

        @Test
        @DisplayName("questionTypes가 null이면 NullPointerException을 던진다")
        void null_questionTypes() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", 5, "medium", null, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("questionTypes");
        }

        @Test
        @DisplayName("applicationQuestions가 null이면 NullPointerException을 던진다")
        void null_applicationQuestions() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", 5, "medium", List.of("project"), null, List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("applicationQuestions");
        }

        @Test
        @DisplayName("documentTexts가 null이면 NullPointerException을 던진다")
        void null_documentTexts() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", 5, "medium", List.of("project"), List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("documentTexts");
        }
    }

    @Nested
    @DisplayName("기본 필드 생성")
    class BasicFields {

        @Test
        @DisplayName("jobRole, companyName, preferredQuestionCount, difficultyLevel이 payload에 포함된다")
        void base_fields_included() throws Exception {
            String payload = builder.build("백엔드 개발자", "카카오", 5, "medium", List.of("project"), List.of(), List.of());
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.get("jobRole").asText()).isEqualTo("백엔드 개발자");
            assertThat(root.get("companyName").asText()).isEqualTo("카카오");
            assertThat(root.get("preferredQuestionCount").asInt()).isEqualTo(5);
            assertThat(root.get("difficultyLevel").asText()).isEqualTo("medium");
        }

        @Test
        @DisplayName("companyName이 null이면 payload에 포함되지 않는다")
        void null_companyName_excluded() throws Exception {
            String payload = builder.build("백엔드 개발자", null, 5, "medium", List.of("project"), List.of(), List.of());
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.has("companyName")).isFalse();
        }
    }

    @Nested
    @DisplayName("applicationQuestions 생성")
    class ApplicationQuestions {

        @Test
        @DisplayName("applicationQuestions가 올바르게 직렬화된다")
        void applicationQuestions_serialized() throws Exception {
            var question = new InterviewQuestionPayloadBuilder.ApplicationQuestionInput(
                    1,
                    "지원 동기를 설명해주세요.",
                    "포트폴리오와 자소서 생성 흐름을 연결한 경험이 있습니다."
            );

            String payload = builder.build("백엔드", null, 3, "medium", List.of("project"), List.of(question), List.of());
            JsonNode questionNode = objectMapper.readTree(payload).get("applicationQuestions").get(0);

            assertThat(questionNode.get("questionOrder").asInt()).isEqualTo(1);
            assertThat(questionNode.get("questionText").asText()).isEqualTo("지원 동기를 설명해주세요.");
            assertThat(questionNode.get("finalAnswerText").asText())
                    .isEqualTo("포트폴리오와 자소서 생성 흐름을 연결한 경험이 있습니다.");
        }
    }

    @Nested
    @DisplayName("portfolioEvidence 생성")
    class PortfolioEvidence {

        @Test
        @DisplayName("문서가 doc_1, doc_2 형태의 evidence로 변환된다")
        void documents_become_evidence() throws Exception {
            String payload = builder.build(
                    "백엔드",
                    null,
                    3,
                    "medium",
                    List.of("project"),
                    List.of(),
                    List.of("문서1", "문서2")
            );
            JsonNode evidence = objectMapper.readTree(payload).get("portfolioEvidence");

            assertThat(evidence.get(0).get("projectKey").asText()).isEqualTo("doc_1");
            assertThat(evidence.get(0).get("evidenceBullets").get(0).asText()).isEqualTo("문서1");
            assertThat(evidence.get(1).get("projectKey").asText()).isEqualTo("doc_2");
            assertThat(evidence.get(1).get("confidence").asText()).isEqualTo("medium");
        }
    }

    @Nested
    @DisplayName("enumCandidates와 constraints 생성")
    class Contracts {

        @Test
        @DisplayName("enumCandidates가 요청 enum과 qualityFlags 후보를 포함한다")
        void enum_candidates_included() throws Exception {
            String payload = builder.build(
                    "백엔드",
                    null,
                    4,
                    "hard",
                    List.of("project", "follow_up"),
                    List.of(),
                    List.of()
            );
            JsonNode enumCandidates = objectMapper.readTree(payload).get("enumCandidates");

            assertThat(arrayTexts(enumCandidates.get("difficultyLevels"))).containsExactly("hard");
            assertThat(arrayTexts(enumCandidates.get("questionTypes"))).containsExactly("project", "follow_up");
            assertThat(arrayTexts(enumCandidates.get("qualityFlags")))
                    .containsExactly("low_context", "weak_evidence", "missing_company_context", "duplicate_risk");
        }

        @Test
        @DisplayName("constraints가 출력 계약을 포함한다")
        void constraints_included() throws Exception {
            String payload = builder.build(
                    "백엔드",
                    null,
                    4,
                    "medium",
                    List.of("project"),
                    List.of(),
                    List.of()
            );
            JsonNode constraints = objectMapper.readTree(payload).get("constraints");

            assertThat(constraints.get("exactQuestionCount").asInt()).isEqualTo(4);
            assertThat(constraints.get("allowAdditionalTopLevelProperties").asBoolean()).isFalse();
            assertThat(constraints.get("allowAdditionalQuestionProperties").asBoolean()).isFalse();
            assertThat(arrayTexts(constraints.get("topLevelRequiredKeys"))).containsExactly("questions", "qualityFlags");
            assertThat(arrayTexts(constraints.get("questionRequiredKeys")))
                    .containsExactly("questionOrder", "questionType", "difficultyLevel", "questionText");
        }
    }

    private List<String> arrayTexts(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }
}
