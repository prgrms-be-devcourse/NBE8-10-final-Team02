package com.back.backend.domain.interview.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.interview.service.InterviewCompletionFollowupGenerationService;
import com.back.backend.domain.interview.service.InterviewResultGenerationService;
import com.back.backend.domain.interview.service.InterviewSessionService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("manual")
@Transactional
class InterviewSessionCompleteManualApiTest extends ApiTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-07T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String EXPECTED_COMPLETION_QUESTION = "성과를 어떤 지표로 확인했는지 설명해주실 수 있나요?";
    private static final String TEST_MODEL = "gemini-2.5-flash";

    private static final String Q1 =
            "백엔드 개발자로서 가장 중요하다고 생각하는 핵심 역량은 무엇이며, 그 역량을 키우기 위해 어떤 구체적인 노력을 하셨는지 경험을 바탕으로 설명해 주십시오.";
    private static final String A1 =
            "저는 원인 파악과 안정적인 구조 설계 역량이 가장 중요하다고 봅니다. 실제로 장애 로그를 유형별로 나누고 재현 테스트를 붙이며, 임시 수정보다 재발 방지 중심으로 백엔드를 개선해 왔습니다.";
    private static final String Q2 =
            "분산 시스템 환경에서 데이터 일관성을 유지하기 위한 전략에는 어떤 것들이 있으며, 각 전략의 장단점은 무엇이라고 생각하시나요?";
    private static final String A2 =
            "대표적으로 2PC와 Saga가 있습니다. 2PC는 일관성은 강하지만 지연과 장애 전파에 약하고, Saga는 유연하고 확장성은 좋지만 보상 트랜잭션 설계가 어렵다는 단점이 있습니다.";
    private static final String Q3 =
            "백엔드 프레임워크를 선택할 때 가장 중요하게 고려하는 요소는 무엇이며, 특정 프레임워크를 사용해 본 경험이 있다면, 해당 프레임워크를 선택한 이유와 장단점을 설명해주세요.";
    private static final String A3 =
            "저는 생산성보다 팀 적합성과 운영 안정성을 더 봅니다. Spring Boot는 구조화, 테스트, DI가 강해 협업에 좋았고, 초기 설정이 무겁다는 단점은 있었습니다.";
    private static final String Q4 =
            "그 기술을 선택할 때 다른 대안과는 어떻게 비교했는지 설명해주실 수 있나요?";
    private static final String A4 =
            "네. 당시에는 Express도 검토했지만, 규모가 커질수록 구조 일관성과 테스트 지원이 중요하다고 봐서, 운영 안정성이 더 높은 Spring Boot를 선택했습니다.";
    private static final String Q5 =
            "이전에 참여했던 프로젝트나 경험에서 성능 최적화를 위해 어떤 노력을 했고, 그 결과는 어떠했는지 구체적인 사례를 들어 설명해 주십시오.";
    private static final String A5 =
            "AI 응답 저장 API에서 불필요한 재조회와 중복 파싱 구간을 줄여 지연을 낮췄습니다. 응답 흐름을 단순화한 뒤 처리 시간이 안정됐고, 실패 시 복구도 쉬워졌습니다.";
    private static final String Q6 =
            "그 프로젝트에서 본인이 맡은 핵심 역할과 기여를 조금 더 구체적으로 설명해주실 수 있나요?";
    private static final String A6 =
            "저는 백엔드 응답 흐름을 설계하고 병목 구간을 직접 찾는 역할을 맡았습니다. 재조회와 중복 처리 로직을 줄여 성능과 안정성을 함께 개선했습니다.";
    private static final String Q7 =
            "팀 프로젝트 진행 중 기술적인 의견 충돌이 발생했을 때, 어떻게 문제를 해결하고 합의점을 도출했는지 경험을 바탕으로 설명해주세요.";
    private static final String A7 =
            "실시간성보다 안정성이 중요한 상황에서 구현 방식 의견이 갈렸습니다. 저는 장단점과 장애 가능성을 비교해 기준을 맞췄고, 작은 범위로 먼저 검증한 뒤 합의했습니다.";
    private static final String Q8 =
            "그 문제의 근본 원인을 어떻게 파악했는지 조금 더 구체적으로 설명해주실 수 있나요?";
    private static final String A8 =
            "실패 로그를 유형별로 분리해 보고, 끊김·필드 누락·형식 혼합 패턴을 비교했습니다. 이후 재현 테스트로 원인을 검증해 근본 원인을 좁혔습니다.";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewSessionService interviewSessionService;

    @Autowired
    private InterviewCompletionFollowupPayloadBuilder completionFollowupPayloadBuilder;

    @MockitoBean
    private InterviewResultGenerationService interviewResultGenerationService;

    @DynamicPropertySource
    static void overrideAiProperties(DynamicPropertyRegistry registry) {
        String apiKey = resolveApiKey();
        registry.add("ai.provider", () -> "gemini");
        registry.add("ai.fallback-provider", () -> "");
        registry.add("ai.gemini.api-key", () -> apiKey == null ? "missing-manual-key" : apiKey);
        registry.add("ai.gemini.base-url", () -> GEMINI_BASE_URL);
        registry.add("ai.gemini.model", () -> TEST_MODEL);
    }

    @BeforeEach
    void setUpManualPreconditions() {
        assumeTrue(isManualRunEnabled(), "manual.ai=true 또는 MANUAL_AI=true 일 때만 실행합니다.");
        assumeTrue(resolveApiKey() != null && !resolveApiKey().isBlank(), "GEMINI_API_KEY가 필요합니다.");

        enableDebugLogs();
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void completeSession_insertsCompletionFollowupInRealApiFlow() throws Exception {
        UserFixture fixture = persistManualCompleteFlowSession("manual-complete-api");
        InterviewSession session = fixture.session();

        System.out.println("=== REAL COMPLETE PAYLOAD ===");
        System.out.println(buildCompletionFollowupPayload(session, fixture.user().getId()));

        given(interviewResultGenerationService.generate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()
        )).willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                87,
                "manual api fallback result",
                List.of(
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(1, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(2, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(3, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(4, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(5, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(6, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(7, 85, "manual", List.of()),
                        new InterviewResultGenerationService.GeneratedInterviewAnswerResult(8, 85, "manual", List.of())
                )
        ));

        MvcResult completeResult = mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andReturn();

        System.out.println("=== COMPLETE API STATUS === " + completeResult.getResponse().getStatus());
        System.out.println("=== COMPLETE API BODY ===");
        System.out.println(completeResult.getResponse().getContentAsString());

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        long totalQuestionCount = entityManager.createQuery(
                        "select count(q) from InterviewSessionQuestion q where q.session.id = :sessionId",
                        Long.class
                )
                .setParameter("sessionId", session.getId())
                .getSingleResult();

        if (completeResult.getResponse().getStatus() == HttpStatus.BAD_REQUEST.value()) {
            assertThat(completeResult.getResponse().getContentAsString())
                    .contains(ErrorCode.REQUEST_VALIDATION_FAILED.name())
                    .contains("remainingQuestionCount");
            assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
            assertThat(refreshedSession.getCompletionFollowupReviewedAt()).isNotNull();
            assertThat(totalQuestionCount).isEqualTo(9L);

            MvcResult detailResult = mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                            .with(authenticated(fixture.user().getId())))
                    .andReturn();

            System.out.println("=== SESSION DETAIL BODY ===");
            System.out.println(detailResult.getResponse().getContentAsString());

            assertThat(detailResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(detailResult.getResponse().getContentAsString())
                    .contains("\"questionOrder\":7")
                    .contains("\"questionType\":\"follow_up\"")
                    .contains(EXPECTED_COMPLETION_QUESTION);

            then(interviewResultGenerationService).should(never())
                    .generate(
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyList(),
                            org.mockito.ArgumentMatchers.anyString()
                    );
            return;
        }

        if (completeResult.getResponse().getStatus() == HttpStatus.OK.value()) {
            assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.FEEDBACK_COMPLETED);
            assertThat(refreshedSession.getCompletionFollowupReviewedAt()).isNotNull();
            assertThat(totalQuestionCount).isEqualTo(8L);
            then(interviewResultGenerationService).should(times(1))
                    .generate(
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyList(),
                            org.mockito.ArgumentMatchers.anyString()
                    );
            return;
        }

        fail("예상하지 못한 complete 응답 상태입니다: " + completeResult.getResponse().getStatus());
    }

    @SuppressWarnings("unchecked")
    private String buildCompletionFollowupPayload(InterviewSession session, long userId) {
        List<InterviewAnswer> answers = entityManager.createQuery(
                        """
                                select a
                                from InterviewAnswer a
                                join fetch a.sessionQuestion q
                                left join fetch q.parentSessionQuestion
                                where a.session.id = :sessionId
                                order by a.answerOrder asc
                                """,
                        InterviewAnswer.class
                )
                .setParameter("sessionId", session.getId())
                .getResultList();

        List<Object> answeredThreads = (List<Object>) ReflectionTestUtils.invokeMethod(
                interviewSessionService,
                "buildAnsweredQuestionThreads",
                answers
        );
        InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest request =
                (InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest)
                        ReflectionTestUtils.invokeMethod(
                                interviewSessionService,
                                "buildCompletionFollowupGenerationRequest",
                                session,
                                answeredThreads
                        );

        return completionFollowupPayloadBuilder.build(
                request.jobRole(),
                request.companyName(),
                request.answeredThreads()
        );
    }

    private void enableDebugLogs() {
        setLoggerDebugLevel("com.back.backend.domain.ai.client.gemini.GeminiClient");
        setLoggerDebugLevel("com.back.backend.domain.ai.template.PromptLoader");
        setLoggerDebugLevel("com.back.backend.domain.ai.pipeline.AiPipeline");
    }

    private void setLoggerDebugLevel(String loggerName) {
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext) {
            Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
            logger.setLevel(Level.DEBUG);
        }
    }

    private boolean isManualRunEnabled() {
        if (Boolean.getBoolean("manual.ai")) {
            return true;
        }
        String envValue = System.getenv("MANUAL_AI");
        return envValue != null && Boolean.parseBoolean(envValue);
    }

    private static String resolveApiKey() {
        String envValue = System.getenv("GEMINI_API_KEY");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        for (Path candidate : List.of(
                Path.of(".env.dev"),
                Path.of("..", ".env.dev")
        )) {
            String loaded = loadApiKeyFromDotEnv(candidate);
            if (loaded != null && !loaded.isBlank()) {
                return loaded;
            }
        }

        return null;
    }

    private static String loadApiKeyFromDotEnv(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }

            for (String rawLine : Files.readAllLines(path)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.startsWith("GEMINI_API_KEY=")) {
                    continue;
                }

                String value = line.substring("GEMINI_API_KEY=".length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private UserFixture persistManualCompleteFlowSession(String prefix) {
        com.back.backend.domain.user.entity.User user = persistUserFixture(prefix).user();
        Application application = persistApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);

        InterviewQuestion q1 = persistQuestion(questionSet, 1, Q1);
        InterviewQuestion q2 = persistQuestion(questionSet, 2, Q2);
        InterviewQuestion q3 = persistQuestion(questionSet, 3, Q3);
        InterviewQuestion q5 = persistQuestion(questionSet, 5, Q5);
        InterviewQuestion q7 = persistQuestion(questionSet, 7, Q7);

        InterviewSession session = persistSessionWithoutSnapshot(user, questionSet, InterviewSessionStatus.IN_PROGRESS);

        InterviewSessionQuestion sq1 = persistSessionQuestion(session, q1, null, 1, InterviewQuestionType.BEHAVIORAL, Q1);
        InterviewSessionQuestion sq2 = persistSessionQuestion(session, q2, null, 2, InterviewQuestionType.TECHNICAL_CS, Q2);
        InterviewSessionQuestion sq3 = persistSessionQuestion(session, q3, null, 3, InterviewQuestionType.TECHNICAL_STACK, Q3);
        InterviewSessionQuestion sq4 = persistSessionQuestion(session, null, sq3, 4, InterviewQuestionType.FOLLOW_UP, Q4);
        InterviewSessionQuestion sq5 = persistSessionQuestion(session, q5, null, 5, InterviewQuestionType.EXPERIENCE, Q5);
        InterviewSessionQuestion sq6 = persistSessionQuestion(session, null, sq5, 6, InterviewQuestionType.FOLLOW_UP, Q6);
        InterviewSessionQuestion sq7 = persistSessionQuestion(session, q7, null, 7, InterviewQuestionType.BEHAVIORAL, Q7);
        InterviewSessionQuestion sq8 = persistSessionQuestion(session, null, sq7, 8, InterviewQuestionType.FOLLOW_UP, Q8);

        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, sq1, 1, A1),
                persistAnswer(session, sq2, 2, A2),
                persistAnswer(session, sq3, 3, A3),
                persistAnswer(session, sq4, 4, A4),
                persistAnswer(session, sq5, 5, A5),
                persistAnswer(session, sq6, 6, A6),
                persistAnswer(session, sq7, 7, A7),
                persistAnswer(session, sq8, 8, A8)
        );

        return new UserFixture(user, questionSet, session, answers);
    }

    private UserFixture persistUserFixture(String prefix) {
        com.back.backend.domain.user.entity.User user = com.back.backend.domain.user.entity.User.builder()
                .email(prefix + "@example.com")
                .displayName(prefix)
                .profileImageUrl("https://example.com/profile.png")
                .status(com.back.backend.domain.user.entity.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return new UserFixture(user, null, null, List.of());
    }

    private Application persistApplication(com.back.backend.domain.user.entity.User user, String title) {
        Application application = Application.builder()
                .user(user)
                .applicationTitle(title)
                .companyName(title + "-company")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);
        entityManager.flush();
        return application;
    }

    private InterviewQuestionSet persistQuestionSet(
            com.back.backend.domain.user.entity.User user,
            Application application
    ) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("질문 세트")
                .questionCount(5)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"behavioral", "experience", "technical_stack", "technical_cs"})
                .build();
        entityManager.persist(questionSet);
        entityManager.flush();
        return questionSet;
    }

    private InterviewQuestion persistQuestion(InterviewQuestionSet questionSet, int order, String questionText) {
        InterviewQuestionType questionType = switch (order) {
            case 1, 7 -> InterviewQuestionType.BEHAVIORAL;
            case 2 -> InterviewQuestionType.TECHNICAL_CS;
            case 3 -> InterviewQuestionType.TECHNICAL_STACK;
            case 5 -> InterviewQuestionType.EXPERIENCE;
            default -> InterviewQuestionType.PROJECT;
        };

        InterviewQuestion question = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(order)
                .questionType(questionType)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        entityManager.persist(question);
        entityManager.flush();
        return question;
    }

    private InterviewSession persistSessionWithoutSnapshot(
            com.back.backend.domain.user.entity.User user,
            InterviewQuestionSet questionSet,
            InterviewSessionStatus status
    ) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(FIXED_NOW)
                .lastActivityAt(FIXED_NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private InterviewSessionQuestion persistSessionQuestion(
            InterviewSession session,
            InterviewQuestion sourceQuestion,
            InterviewSessionQuestion parentSessionQuestion,
            int questionOrder,
            InterviewQuestionType questionType,
            String questionText
    ) {
        InterviewSessionQuestion sessionQuestion = InterviewSessionQuestion.builder()
                .session(session)
                .sourceQuestion(sourceQuestion)
                .parentSessionQuestion(parentSessionQuestion)
                .questionOrder(questionOrder)
                .questionType(questionType)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        entityManager.persist(sessionQuestion);
        entityManager.flush();
        return sessionQuestion;
    }

    private InterviewAnswer persistAnswer(
            InterviewSession session,
            InterviewSessionQuestion question,
            int answerOrder,
            String answerText
    ) {
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .sessionQuestion(question)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(false)
                .build();
        entityManager.persist(answer);
        entityManager.flush();
        return answer;
    }

    private record UserFixture(
            com.back.backend.domain.user.entity.User user,
            InterviewQuestionSet questionSet,
            InterviewSession session,
            List<InterviewAnswer> answers
    ) {
    }
}
