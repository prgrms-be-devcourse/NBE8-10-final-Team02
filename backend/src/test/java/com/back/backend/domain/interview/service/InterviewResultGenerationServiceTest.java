package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.portfolio.service.FailedJobRedisStore;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.FeedbackTagCategory;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InterviewResultGenerationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AiPipeline aiPipeline;

    @Mock
    private FeedbackTagRepository feedbackTagRepository;

    @Mock
    private FailedJobRedisStore failedJobRedisStore;

    private InterviewResultGenerationService interviewResultGenerationService;

    @BeforeEach
    void setUp() {
        interviewResultGenerationService =
                new InterviewResultGenerationService(aiPipeline, feedbackTagRepository, failedJobRedisStore);
    }

    @Test
    void generate_returnsMappedInterviewResult() throws Exception {
        List<FeedbackTag> tagMaster = List.of(
                feedbackTag("근거 부족", FeedbackTagCategory.EVIDENCE),
                feedbackTag("구체성 부족", FeedbackTagCategory.CONTENT),
                feedbackTag("선택 근거 부족", FeedbackTagCategory.TECHNICAL)
        );
        List<InterviewAnswer> answers = List.of(
                answer(1, "첫 번째 질문", "첫 번째 답변"),
                answer(2, "두 번째 질문", "두 번째 답변")
        );

        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(tagMaster);
        given(aiPipeline.execute(eq("ai.interview.evaluate.v1"), anyString(), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "totalScore": 84,
                          "summaryFeedback": "근거 제시는 좋았지만 일부 답변은 더 구체화가 필요합니다.",
                          "answers": [
                            {
                              "questionOrder": 1,
                              "score": 80,
                              "evaluationRationale": "핵심 근거는 있으나 수치가 부족합니다.",
                              "tagNames": ["근거 부족"]
                            },
                            {
                              "questionOrder": 2,
                              "score": 88,
                              "evaluationRationale": "선택 이유 설명은 괜찮지만 사례가 더 필요합니다.",
                              "tagNames": ["구체성 부족", "선택 근거 부족"]
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        InterviewResultGenerationService.GeneratedInterviewResult result =
                interviewResultGenerationService.generate(1L, 901L, 301L, answers, "백엔드 개발자");

        assertThat(result.totalScore()).isEqualTo(84);
        assertThat(result.summaryFeedback()).isEqualTo("근거 제시는 좋았지만 일부 답변은 더 구체화가 필요합니다.");
        assertThat(result.answers()).hasSize(2);
        assertThat(result.answers().get(0).answerOrder()).isEqualTo(1);
        assertThat(result.answers().get(0).tagNames()).containsExactly("근거 부족");
        assertThat(result.answers().get(1).answerOrder()).isEqualTo(2);
        assertThat(result.answers().get(1).tagNames()).containsExactly("구체성 부족", "선택 근거 부족");
    }

    @Test
    void generate_throwsIncompleteWhenAnswerOrderIsDuplicated() throws Exception {
        List<FeedbackTag> tagMaster = List.of(feedbackTag("근거 부족", FeedbackTagCategory.EVIDENCE));
        List<InterviewAnswer> answers = List.of(
                answer(1, "첫 번째 질문", "첫 번째 답변"),
                answer(2, "두 번째 질문", "두 번째 답변")
        );

        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(tagMaster);
        given(aiPipeline.execute(eq("ai.interview.evaluate.v1"), anyString(), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "totalScore": 70,
                          "summaryFeedback": "중복된 질문 순번입니다.",
                          "answers": [
                            {
                              "questionOrder": 1,
                              "score": 70,
                              "evaluationRationale": "첫 번째 평가",
                              "tagNames": ["근거 부족"]
                            },
                            {
                              "questionOrder": 1,
                              "score": 60,
                              "evaluationRationale": "중복 평가",
                              "tagNames": ["근거 부족"]
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        assertThatThrownBy(() -> interviewResultGenerationService.generate(1L, 901L, 301L, answers, "백엔드 개발자"))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INTERVIEW_RESULT_INCOMPLETE));
    }

    @Test
    void generate_throwsIncompleteWhenTagNameIsUnknown() throws Exception {
        List<FeedbackTag> tagMaster = List.of(feedbackTag("근거 부족", FeedbackTagCategory.EVIDENCE));
        List<InterviewAnswer> answers = List.of(answer(1, "첫 번째 질문", "첫 번째 답변"));

        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(tagMaster);
        given(aiPipeline.execute(eq("ai.interview.evaluate.v1"), anyString(), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "totalScore": 70,
                          "summaryFeedback": "알 수 없는 태그가 포함됩니다.",
                          "answers": [
                            {
                              "questionOrder": 1,
                              "score": 70,
                              "evaluationRationale": "태그가 잘못되었습니다.",
                              "tagNames": ["없는 태그"]
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        assertThatThrownBy(() -> interviewResultGenerationService.generate(1L, 901L, 301L, answers, "백엔드 개발자"))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INTERVIEW_RESULT_INCOMPLETE));
    }

    @Test
    void generate_throws503WhenAiClientIsUnavailable() {
        List<FeedbackTag> tagMaster = List.of(feedbackTag("근거 부족", FeedbackTagCategory.EVIDENCE));
        List<InterviewAnswer> answers = List.of(answer(1, "첫 번째 질문", "첫 번째 답변"));

        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(tagMaster);
        given(aiPipeline.execute(eq("ai.interview.evaluate.v1"), anyString(), anyString()))
                .willThrow(new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        "AI provider unavailable"
                ));

        assertThatThrownBy(() -> interviewResultGenerationService.generate(1L, 901L, 301L, answers, "백엔드 개발자"))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> {
                    ServiceException serviceException = (ServiceException) exception;
                    assertThat(serviceException.getErrorCode())
                            .isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
                    assertThat(serviceException.getStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void generate_throwsGenerationFailedWhenTagMasterIsMissing() {
        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(List.of());

        assertThatThrownBy(() -> interviewResultGenerationService.generate(1L, 901L, 301L, List.of(), "백엔드 개발자"))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INTERVIEW_RESULT_GENERATION_FAILED));
    }

    @Test
    void generate_includesDynamicFollowupAnswerInEvaluatePayload() throws Exception {
        List<FeedbackTag> tagMaster = List.of(feedbackTag("근거 부족", FeedbackTagCategory.EVIDENCE));
        List<InterviewAnswer> answers = List.of(
                answer(1, "첫 번째 질문", "첫 번째 답변", InterviewQuestionType.PROJECT),
                answer(2, "방금 답변한 선택 기준을 조금 더 구체적으로 설명해주실 수 있나요?", "두 번째 답변", InterviewQuestionType.FOLLOW_UP)
        );

        given(feedbackTagRepository.findAllByOrderByIdAsc()).willReturn(tagMaster);
        given(aiPipeline.execute(eq("ai.interview.evaluate.v1"), anyString(), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "totalScore": 82,
                          "summaryFeedback": "follow-up 답변까지 포함해 평가했습니다.",
                          "answers": [
                            {
                              "questionOrder": 1,
                              "score": 80,
                              "evaluationRationale": "첫 번째 평가",
                              "tagNames": ["근거 부족"]
                            },
                            {
                              "questionOrder": 2,
                              "score": 84,
                              "evaluationRationale": "두 번째 평가",
                              "tagNames": []
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        InterviewResultGenerationService.GeneratedInterviewResult result =
                interviewResultGenerationService.generate(1L, 901L, 301L, answers, "백엔드 개발자");

        assertThat(result.answers()).hasSize(2);
        then(aiPipeline).should().execute(eq("ai.interview.evaluate.v1"), argThat(payload ->
                payload.contains("\"questionType\":\"follow_up\"")
                        && payload.contains("\"questionText\":\"방금 답변한 선택 기준을 조금 더 구체적으로 설명해주실 수 있나요?\"")
        ), anyString());
    }

    private FeedbackTag feedbackTag(String tagName, FeedbackTagCategory category) {
        FeedbackTag tag = FeedbackTag.builder()
                .tagName(tagName)
                .tagCategory(category)
                .description(tagName + " 설명")
                .build();
        ReflectionTestUtils.setField(tag, "id", (long) Math.abs(tagName.hashCode()));
        return tag;
    }

    private InterviewAnswer answer(int answerOrder, String questionText, String answerText) {
        return answer(answerOrder, questionText, answerText, InterviewQuestionType.PROJECT);
    }

    private InterviewAnswer answer(
            int answerOrder,
            String questionText,
            String answerText,
            InterviewQuestionType questionType
    ) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .title("질문 세트")
                .questionCount(2)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"technical_stack"})
                .build();
        InterviewSession session = InterviewSession.builder()
                .questionSet(questionSet)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .build();
        InterviewSessionQuestion sessionQuestion = InterviewSessionQuestion.builder()
                .session(session)
                .questionOrder(answerOrder)
                .questionType(questionType)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        ReflectionTestUtils.setField(sessionQuestion, "id", (long) answerOrder);

        return InterviewAnswer.builder()
                .sessionQuestion(sessionQuestion)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(false)
                .build();
    }
}
