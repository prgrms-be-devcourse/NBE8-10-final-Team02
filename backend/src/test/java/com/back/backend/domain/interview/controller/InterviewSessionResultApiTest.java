package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionResultApiTest extends ApiTestBase {

    private static final Instant STARTED_AT = Instant.parse("2026-03-25T09:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-03-25T09:15:00Z");

    @Autowired
    private TestFixtures fixtures;

    @Test
    void getSessionResult_returns200ForFeedbackCompletedSession() throws Exception {
        ResultFixture fixture = persistFeedbackCompletedFixture("result-success");

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}/result", fixture.session().getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getId()))
                .andExpect(jsonPath("$.data.questionSetId").value(fixture.questionSet().getId()))
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(84))
                .andExpect(jsonPath("$.data.summaryFeedback")
                        .value("구조는 좋았고 경험 기반 근거를 더 구체화하면 좋습니다."))
                .andExpect(jsonPath("$.data.answers", hasSize(3)))
                .andExpect(jsonPath("$.data.answers[0].answerId").value(fixture.answers().get(0).getId()))
                .andExpect(jsonPath("$.data.answers[0].questionId").value(fixture.questions().get(0).getId()))
                .andExpect(jsonPath("$.data.answers[0].questionText").value("첫 번째 질문"))
                .andExpect(jsonPath("$.data.answers[0].answerText").value("첫 번째 답변입니다. 결과 조회 응답 검증용으로 충분히 긴 답변입니다."))
                .andExpect(jsonPath("$.data.answers[0].score").value(80))
                .andExpect(jsonPath("$.data.answers[0].evaluationRationale")
                        .value("핵심 설명은 있었지만 수치 근거가 더 필요합니다."))
                .andExpect(jsonPath("$.data.answers[0].tags", hasSize(1)))
                .andExpect(jsonPath("$.data.answers[0].tags[0].tagId").value(fixture.tags().get(0).getId()))
                .andExpect(jsonPath("$.data.answers[0].tags[0].tagName").value("근거 부족"))
                .andExpect(jsonPath("$.data.answers[0].tags[0].tagCategory").value("evidence"))
                .andExpect(jsonPath("$.data.answers[1].tags", hasSize(1)))
                .andExpect(jsonPath("$.data.answers[1].tags[0].tagName").value("구체성 부족"))
                .andExpect(jsonPath("$.data.answers[2].tags", hasSize(0)))
                .andExpect(jsonPath("$.data.startedAt").value(STARTED_AT.toString()))
                .andExpect(jsonPath("$.data.endedAt").value(ENDED_AT.toString()));
    }

    @Test
    void getSessionResult_returns404WhenSessionIsNotOwned() throws Exception {
        ResultFixture fixture = persistFeedbackCompletedFixture("result-not-owned");
        User otherUser = fixtures.createUser("result-other@example.com", "result-other");

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}/result", fixture.session().getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void getSessionResult_returns409WhenResultIsNotReady() throws Exception {
        ResultFixture fixture = persistCompletedFixture("result-incomplete");

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}/result", fixture.session().getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_RESULT_INCOMPLETE.name()))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private ResultFixture persistFeedbackCompletedFixture(String prefix) {
        User user = fixtures.createUser(prefix + "@example.com", prefix);
        Application application = fixtures.createApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 3);
        List<InterviewQuestion> questions = List.of(
                fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문"),
                fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문"),
                fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문")
        );
        InterviewSession session = fixtures.createInterviewSession(
                user, questionSet,
                InterviewSessionStatus.FEEDBACK_COMPLETED,
                84, "구조는 좋았고 경험 기반 근거를 더 구체화하면 좋습니다.",
                STARTED_AT, ENDED_AT
        );
        List<InterviewAnswer> answers = List.of(
                fixtures.createEvaluatedAnswer(session, questions.get(0), 1,
                        "첫 번째 답변입니다. 결과 조회 응답 검증용으로 충분히 긴 답변입니다.",
                        80,
                        "핵심 설명은 있었지만 수치 근거가 더 필요합니다."),
                fixtures.createEvaluatedAnswer(session, questions.get(1), 2,
                        "두 번째 답변입니다. 결과 조회 응답 검증용으로 충분히 긴 답변입니다.",
                        86,
                        "문제 해결 흐름은 명확하지만 사례를 더 압축하면 좋습니다."),
                fixtures.createEvaluatedAnswer(session, questions.get(2), 3,
                        "세 번째 답변입니다. 결과 조회 응답 검증용으로 충분히 긴 답변입니다.",
                        88,
                        "선택 이유는 잘 설명했지만 trade-off를 더 드러낼 수 있습니다.")
        );
        List<FeedbackTag> tags = List.of(
                fixtures.findFeedbackTag("근거 부족"),
                fixtures.findFeedbackTag("구체성 부족")
        );
        fixtures.createAnswerTag(answers.get(0), tags.get(0));
        fixtures.createAnswerTag(answers.get(1), tags.get(1));
        return new ResultFixture(user, questionSet, session, questions, answers, tags);
    }

    private ResultFixture persistCompletedFixture(String prefix) {
        User user = fixtures.createUser(prefix + "@example.com", prefix);
        Application application = fixtures.createApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 3);
        List<InterviewQuestion> questions = List.of(
                fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문"),
                fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문"),
                fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문")
        );
        InterviewSession session = fixtures.createInterviewSession(
                user, questionSet,
                InterviewSessionStatus.COMPLETED,
                null, null,
                STARTED_AT, ENDED_AT
        );
        List<InterviewAnswer> answers = List.of(
                fixtures.createEvaluatedAnswer(session, questions.get(0), 1,
                        "첫 번째 답변입니다. 결과 준비 전 상태 검증용 답변입니다.",
                        80,
                        "핵심 설명은 있었지만 수치 근거가 더 필요합니다."),
                fixtures.createEvaluatedAnswer(session, questions.get(1), 2,
                        "두 번째 답변입니다. 결과 준비 전 상태 검증용 답변입니다.",
                        86,
                        "문제 해결 흐름은 명확하지만 사례를 더 압축하면 좋습니다."),
                fixtures.createEvaluatedAnswer(session, questions.get(2), 3,
                        "세 번째 답변입니다. 결과 준비 전 상태 검증용 답변입니다.",
                        88,
                        "선택 이유는 잘 설명했지만 trade-off를 더 드러낼 수 있습니다.")
        );
        return new ResultFixture(user, questionSet, session, questions, answers, List.of());
    }

    private record ResultFixture(
            User user,
            InterviewQuestionSet questionSet,
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            List<FeedbackTag> tags
    ) {
    }
}
