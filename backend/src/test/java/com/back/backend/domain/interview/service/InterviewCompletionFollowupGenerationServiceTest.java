package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewCompletionFollowupGenerationServiceTest {

    private static final String TEMPLATE_ID = "ai.interview.followup.complete.v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AiPipeline aiPipeline;

    private InterviewCompletionFollowupGenerationService interviewCompletionFollowupGenerationService;

    @BeforeEach
    void setUp() {
        interviewCompletionFollowupGenerationService = new InterviewCompletionFollowupGenerationService(
                aiPipeline,
                new InterviewCompletionFollowupPayloadBuilder()
        );
    }

    @Test
    void generate_returnsMappedDecisionWhenParentOrderIsAllowed() throws Exception {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": {
                            "questionType": "follow_up",
                            "difficultyLevel": "medium",
                            "questionText": "그 기준을 실제 운영팀과 어떻게 맞췄는지 조금 더 구체적으로 설명해주실 수 있나요?",
                            "parentQuestionOrder": 2
                          },
                          "qualityFlags": []
                        }
                        """));

        var generated = interviewCompletionFollowupGenerationService.generate(baseRequest());

        assertThat(generated).isNotNull();
        assertThat(generated.parentQuestionOrder()).isEqualTo(2);
        assertThat(generated.followupDraft().questionType()).isEqualTo(InterviewQuestionType.FOLLOW_UP);
        assertThat(generated.followupDraft().difficultyLevel()).isEqualTo(DifficultyLevel.MEDIUM);
        assertThat(generated.followupDraft().questionText())
                .isEqualTo("그 기준을 실제 운영팀과 어떻게 맞췄는지 조금 더 구체적으로 설명해주실 수 있나요?");
    }

    @Test
    void generate_returnsNullWhenAiReturnsParentOrderOutsideAllowedTailOrders() throws Exception {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": {
                            "questionType": "follow_up",
                            "difficultyLevel": "medium",
                            "questionText": "허용되지 않은 parent order입니다.",
                            "parentQuestionOrder": 99
                          },
                          "qualityFlags": []
                        }
                        """));

        var generated = interviewCompletionFollowupGenerationService.generate(baseRequest());

        assertThat(generated).isNull();
    }

    @Test
    void generate_throws503WhenAiClientIsUnavailable() {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willThrow(new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        "AI provider unavailable"
                ));

        assertThatThrownBy(() -> interviewCompletionFollowupGenerationService.generate(baseRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> {
                    ServiceException serviceException = (ServiceException) exception;
                    assertThat(serviceException.getErrorCode())
                            .isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
                    assertThat(serviceException.getStatus().value()).isEqualTo(503);
                });
    }

    private InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest baseRequest() {
        return new InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest(
                "Backend Engineer",
                "OpenAI",
                List.of(new InterviewCompletionFollowupPayloadBuilder.AnsweredThread(
                        2,
                        new InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(
                                1,
                                "project",
                                "프로젝트에서 어떤 기준으로 접근 방식을 선택하셨나요?",
                                "medium"
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadAnswer(
                                "프로젝트 요구사항과 운영팀 적응 비용을 같이 보고 선택했습니다.",
                                false
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.RuntimeRuleSummary(
                                "USE_CANDIDATE",
                                "REASON",
                                "RESULT",
                                List.of("PROJECT_APPROACH_REASON")
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(
                                2,
                                "follow_up",
                                "그 접근 방식을 선택한 이유를 조금 더 구체적으로 설명해주실 수 있나요?",
                                "medium"
                        ),
                        new InterviewCompletionFollowupPayloadBuilder.ThreadAnswer(
                                "일정 영향과 운영팀의 확인 흐름을 같이 봤습니다.",
                                false
                        )
                ))
        );
    }
}
