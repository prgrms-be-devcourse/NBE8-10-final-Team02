package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewCompletionFollowupPayloadBuilderTest {

    private final InterviewCompletionFollowupPayloadBuilder builder = new InterviewCompletionFollowupPayloadBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("root-only thread는 runtime follow-up 없이 직렬화된다")
    void build_serializesRootOnlyThread() throws Exception {
        String payload = builder.build(
                "백엔드 개발자",
                "카카오",
                List.of(new InterviewCompletionFollowupPayloadBuilder.AnsweredThread(
                        3,
                        new InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(3, "project", "프로젝트 질문", "medium"),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadAnswer("프로젝트 답변", false),
                        new InterviewCompletionFollowupPayloadBuilder.RuntimeRuleSummary(
                                "NO_FOLLOW_UP",
                                null,
                                null,
                                List.of()
                        ),
                        null,
                        null
                ))
        );

        JsonNode thread = objectMapper.readTree(payload).path("answeredThreads").get(0);

        assertThat(thread.path("tailQuestionOrder").asInt()).isEqualTo(3);
        assertThat(thread.path("rootQuestion").path("questionOrder").asInt()).isEqualTo(3);
        assertThat(thread.path("runtimeRuleSummary").path("finalAction").asText()).isEqualTo("NO_FOLLOW_UP");
        assertThat(thread.path("runtimeFollowupQuestion").isMissingNode()).isTrue();
        assertThat(thread.path("runtimeFollowupAnswer").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("runtime follow-up이 있는 thread는 tail order와 follow-up Q/A를 함께 직렬화한다")
    void build_serializesThreadWithRuntimeFollowup() throws Exception {
        String payload = builder.build(
                "백엔드 개발자",
                "카카오",
                List.of(new InterviewCompletionFollowupPayloadBuilder.AnsweredThread(
                        2,
                        new InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(1, "project", "프로젝트 질문", "medium"),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadAnswer("root 답변", false),
                        new InterviewCompletionFollowupPayloadBuilder.RuntimeRuleSummary(
                                "USE_CANDIDATE",
                                "REASON",
                                "RESULT",
                                List.of("PROJECT_APPROACH_REASON")
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(
                                2,
                                "follow_up",
                                "runtime follow-up 질문",
                                "medium"
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadAnswer("runtime follow-up 답변", false)
                ))
        );

        JsonNode thread = objectMapper.readTree(payload).path("answeredThreads").get(0);

        assertThat(thread.path("tailQuestionOrder").asInt()).isEqualTo(2);
        assertThat(thread.path("runtimeFollowupQuestion").path("questionOrder").asInt()).isEqualTo(2);
        assertThat(thread.path("runtimeFollowupQuestion").path("questionType").asText()).isEqualTo("follow_up");
        assertThat(thread.path("runtimeFollowupAnswer").path("answerText").asText()).isEqualTo("runtime follow-up 답변");
        assertThat(thread.path("runtimeRuleSummary").path("candidateQuestionTypes").get(0).asText())
                .isEqualTo("PROJECT_APPROACH_REASON");
    }
}
