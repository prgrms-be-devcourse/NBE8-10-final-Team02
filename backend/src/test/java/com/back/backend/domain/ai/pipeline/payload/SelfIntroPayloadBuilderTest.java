package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfIntroPayloadBuilderTest {

    private SelfIntroPayloadBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new SelfIntroPayloadBuilder(objectMapper);
    }

    @Nested
    @DisplayName("null 파라미터 방어")
    class NullGuard {

        @Test
        @DisplayName("jobRole이 null이면 NullPointerException을 던진다")
        void null_jobRole() {
            assertThatThrownBy(() -> builder.build(null, "회사", List.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobRole");
        }

        @Test
        @DisplayName("questions가 null이면 NullPointerException을 던진다")
        void null_questions() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("questions");
        }

        @Test
        @DisplayName("documentTexts가 null이면 NullPointerException을 던진다")
        void null_documentTexts() {
            assertThatThrownBy(() -> builder.build("백엔드", "회사", List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("documentTexts");
        }
    }

    @Nested
    @DisplayName("기본 필드 생성")
    class BasicFields {

        @Test
        @DisplayName("jobRole과 companyName이 payload에 포함된다")
        void jobRole_and_companyName() throws Exception {
            String payload = builder.build("백엔드 개발자", "카카오", List.of(), List.of());
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.get("jobRole").asText()).isEqualTo("백엔드 개발자");
            assertThat(root.get("companyName").asText()).isEqualTo("카카오");
        }

        @Test
        @DisplayName("companyName이 null이면 payload에 포함되지 않는다")
        void null_companyName_excluded() throws Exception {
            String payload = builder.build("백엔드 개발자", null, List.of(), List.of());
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.has("companyName")).isFalse();
        }

        @Test
        @DisplayName("companyName이 공백이면 payload에 포함되지 않는다")
        void blank_companyName_excluded() throws Exception {
            String payload = builder.build("백엔드 개발자", "  ", List.of(), List.of());
            JsonNode root = objectMapper.readTree(payload);

            assertThat(root.has("companyName")).isFalse();
        }
    }

    @Nested
    @DisplayName("questionList 생성")
    class QuestionList {

        @Test
        @DisplayName("문항 정보가 올바르게 직렬화된다")
        void question_serialized_correctly() throws Exception {
            var question = new SelfIntroPayloadBuilder.QuestionInput(
                1, "지원 동기를 작성해주세요.", "formal", "medium", "프로젝트 경험"
            );
            String payload = builder.build("백엔드", null, List.of(question), List.of());
            JsonNode q = objectMapper.readTree(payload).get("questionList").get(0);

            assertThat(q.get("questionOrder").asInt()).isEqualTo(1);
            assertThat(q.get("questionText").asText()).isEqualTo("지원 동기를 작성해주세요.");
            assertThat(q.get("toneOption").asText()).isEqualTo("formal");
            assertThat(q.get("lengthOption").asText()).isEqualTo("medium");
            assertThat(q.get("emphasisPoint").asText()).isEqualTo("프로젝트 경험");
        }

        @Test
        @DisplayName("toneOption, lengthOption, emphasisPoint가 null이면 questionList에 포함되지 않는다")
        void nullable_fields_excluded() throws Exception {
            var question = new SelfIntroPayloadBuilder.QuestionInput(
                1, "지원 동기를 작성해주세요.", null, null, null
            );
            String payload = builder.build("백엔드", null, List.of(question), List.of());
            JsonNode q = objectMapper.readTree(payload).get("questionList").get(0);

            assertThat(q.has("toneOption")).isFalse();
            assertThat(q.has("lengthOption")).isFalse();
            assertThat(q.has("emphasisPoint")).isFalse();
        }

        @Test
        @DisplayName("문항이 없으면 questionList가 빈 배열이다")
        void empty_questionList() throws Exception {
            String payload = builder.build("백엔드", null, List.of(), List.of());
            JsonNode questionList = objectMapper.readTree(payload).get("questionList");

            assertThat(questionList.isArray()).isTrue();
            assertThat(questionList.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("portfolioEvidence 생성")
    class PortfolioEvidence {

        @Test
        @DisplayName("문서가 독립 증거 항목으로 변환된다")
        void document_becomes_evidence() throws Exception {
            String payload = builder.build("백엔드", null, List.of(),
                List.of("Spring Boot 프로젝트 경험"));
            JsonNode ev = objectMapper.readTree(payload).get("portfolioEvidence").get(0);

            assertThat(ev.get("projectKey").asText()).isEqualTo("doc_1");
            assertThat(ev.get("summary").asText()).isEqualTo("Spring Boot 프로젝트 경험");
            assertThat(ev.get("confidence").asText()).isEqualTo("medium");
        }

        @Test
        @DisplayName("여러 문서는 doc_1, doc_2 순으로 key가 부여된다")
        void multiple_documents_indexed() throws Exception {
            String payload = builder.build("백엔드", null, List.of(),
                List.of("문서1", "문서2"));
            JsonNode evidence = objectMapper.readTree(payload).get("portfolioEvidence");

            assertThat(evidence.get(0).get("projectKey").asText()).isEqualTo("doc_1");
            assertThat(evidence.get(1).get("projectKey").asText()).isEqualTo("doc_2");
        }

        @Test
        @DisplayName("문서가 없으면 portfolioEvidence가 빈 배열이다")
        void empty_evidence() throws Exception {
            String payload = builder.build("백엔드", null, List.of(), List.of());
            JsonNode evidence = objectMapper.readTree(payload).get("portfolioEvidence");

            assertThat(evidence.isArray()).isTrue();
            assertThat(evidence.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("writingConstraints 생성")
    class WritingConstraints {

        @Test
        @DisplayName("고정 정책 필드가 올바르게 포함된다")
        void fixed_policy_fields() throws Exception {
            String payload = builder.build("백엔드", null, List.of(), List.of());
            JsonNode constraints = objectMapper.readTree(payload).get("writingConstraints");

            assertThat(constraints.get("forbidMadeUpMetrics").asBoolean()).isTrue();
            assertThat(constraints.get("language").asText()).isEqualTo("ko");
            assertThat(constraints.get("preferStarStructure").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("lengthPolicy의 short/medium/long 글자 수 정책이 포함된다")
        void length_policy_included() throws Exception {
            String payload = builder.build("백엔드", null, List.of(), List.of());
            JsonNode lengthPolicy = objectMapper.readTree(payload)
                .get("writingConstraints").get("lengthPolicy");

            assertThat(lengthPolicy.get("short").get("targetChars").asInt()).isEqualTo(500);
            assertThat(lengthPolicy.get("short").get("hardMaxChars").asInt()).isEqualTo(700);
            assertThat(lengthPolicy.get("medium").get("targetChars").asInt()).isEqualTo(900);
            assertThat(lengthPolicy.get("medium").get("hardMaxChars").asInt()).isEqualTo(1200);
            assertThat(lengthPolicy.get("long").get("targetChars").asInt()).isEqualTo(1400);
            assertThat(lengthPolicy.get("long").get("hardMaxChars").asInt()).isEqualTo(1800);
        }
    }
}
