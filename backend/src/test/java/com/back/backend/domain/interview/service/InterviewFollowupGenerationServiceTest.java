package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewFollowupPayloadBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewFollowupGenerationServiceTest {

    private static final String TEMPLATE_ID = "ai.interview.followup.generate.v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AiPipeline aiPipeline;

    private InterviewFollowupGenerationService interviewFollowupGenerationService;

    @BeforeEach
    void setUp() {
        interviewFollowupGenerationService = new InterviewFollowupGenerationService(
                aiPipeline,
                new InterviewFollowupPayloadBuilder()
        );
    }

    @Test
    void generate_returnsMappedFollowup() throws Exception {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": {
                            "questionType": "follow_up",
                            "difficultyLevel": "medium",
                            "questionText": "그 선택 기준을 조금 더 구체적으로 설명해주실 수 있나요?",
                            "parentQuestionOrder": 3
                          },
                          "qualityFlags": []
                        }
                        """));

        InterviewFollowupGenerationService.GeneratedInterviewFollowup generatedFollowup =
                interviewFollowupGenerationService.generate(baseRequest());

        assertThat(generatedFollowup).isNotNull();
        assertThat(generatedFollowup.questionType()).isEqualTo(InterviewQuestionType.FOLLOW_UP);
        assertThat(generatedFollowup.difficultyLevel()).isEqualTo(DifficultyLevel.MEDIUM);
        assertThat(generatedFollowup.parentQuestionOrder()).isEqualTo(3);
        assertThat(generatedFollowup.questionText()).isEqualTo("그 선택 기준을 조금 더 구체적으로 설명해주실 수 있나요?");
    }

    @Test
    void generate_returnsNullWhenAiReturnsLowContext() throws Exception {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": null,
                          "qualityFlags": ["low_context"]
                        }
                        """));

        InterviewFollowupGenerationService.GeneratedInterviewFollowup generatedFollowup =
                interviewFollowupGenerationService.generate(baseRequest());

        assertThat(generatedFollowup).isNull();
    }

    @Test
    void generate_throws503WhenAiClientIsUnavailable() {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willThrow(new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        "AI provider unavailable"
                ));

        assertThatThrownBy(() -> interviewFollowupGenerationService.generate(baseRequest()))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> {
                    ServiceException serviceException = (ServiceException) exception;
                    assertThat(serviceException.getErrorCode())
                            .isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE);
                    assertThat(serviceException.getStatus().value()).isEqualTo(503);
                });
    }

    private InterviewFollowupGenerationService.FollowupGenerationRequest baseRequest() {
        return new InterviewFollowupGenerationService.FollowupGenerationRequest(
                "Backend Engineer",
                "OpenAI",
                new InterviewFollowupPayloadBuilder.CurrentQuestion(
                        3,
                        "project",
                        "프로젝트에서 어떤 기준으로 기술을 선택하셨나요?",
                        "medium"
                ),
                new InterviewFollowupPayloadBuilder.CurrentAnswer(
                        "프로젝트 요구사항과 팀 숙련도를 같이 보고 선택했습니다.",
                        false
                ),
                0,
                1
        );
    }
}
