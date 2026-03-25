package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder.QuestionInput;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SelfIntroGenerateServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Mock
    private ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;

    @Mock
    private SelfIntroPayloadBuilder payloadBuilder;

    @Mock
    private AiPipeline aiPipeline;

    @InjectMocks
    private SelfIntroGenerateService selfIntroGenerateService;

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 10L;
    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Application application;

    @BeforeEach
    void setUp() {
        application = Application.builder()
            .jobRole("백엔드 개발자")
            .companyName("카카오")
            .build();
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("Application이 존재하지 않으면 APPLICATION_NOT_FOUND 예외를 던진다")
        void application_not_found() {
            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND));
        }

        @Test
        @DisplayName("문항이 없으면 APPLICATION_QUESTION_REQUIRED 예외를 던진다")
        void no_questions() {
            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of());

            assertThatThrownBy(() -> selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_QUESTION_REQUIRED));
        }
    }

    @Nested
    @DisplayName("regenerate=false")
    class RegenerateFalse {

        @Test
        @DisplayName("generatedAnswer가 이미 있는 문항은 건너뛰고, 없는 문항만 AI 호출 대상이 된다")
        void skips_already_generated() throws Exception {
            ApplicationQuestion answered = createQuestion(1, "질문1", "기존 답변",
                ApplicationToneOption.FORMAL, ApplicationLengthOption.MEDIUM, null);
            ApplicationQuestion unanswered = createQuestion(2, "질문2", null,
                ApplicationToneOption.BALANCED, ApplicationLengthOption.SHORT, "프로젝트 강조");

            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(answered, unanswered));
            given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
                .willReturn(List.of());
            given(payloadBuilder.build(anyString(), anyString(), anyList(), anyList()))
                .willReturn("{}");

            JsonNode aiResponse = objectMapper.readTree("""
                {
                  "answers": [
                    { "questionOrder": 2, "answerText": "AI 생성 답변" }
                  ]
                }
                """);
            given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(aiResponse);

            List<ApplicationQuestion> result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result).hasSize(2);
            assertThat(unanswered.getGeneratedAnswer()).isEqualTo("AI 생성 답변");
            assertThat(answered.getGeneratedAnswer()).isEqualTo("기존 답변");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<QuestionInput>> captor = ArgumentCaptor.forClass(List.class);
            verify(payloadBuilder).build(eq("백엔드 개발자"), eq("카카오"), captor.capture(), anyList());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().getFirst().questionOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 문항에 generatedAnswer가 있으면 AI 호출 없이 전체 목록을 반환한다")
        void all_already_generated() {
            ApplicationQuestion answered = createQuestion(1, "질문1", "기존 답변", null, null, null);

            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(answered));

            List<ApplicationQuestion> result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result).hasSize(1);
            verify(aiPipeline, never()).execute(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("regenerate=true")
    class RegenerateTrue {

        @Test
        @DisplayName("기존 답변이 있어도 전체 문항을 AI 호출 대상으로 포함한다")
        void regenerates_all() throws Exception {
            ApplicationQuestion q1 = createQuestion(1, "질문1", "기존 답변",
                ApplicationToneOption.FORMAL, ApplicationLengthOption.LONG, null);
            ApplicationQuestion q2 = createQuestion(2, "질문2", null,
                null, null, null);

            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(q1, q2));
            given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
                .willReturn(List.of());
            given(payloadBuilder.build(anyString(), anyString(), anyList(), anyList()))
                .willReturn("{}");

            JsonNode aiResponse = objectMapper.readTree("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "새 답변1" },
                    { "questionOrder": 2, "answerText": "새 답변2" }
                  ]
                }
                """);
            given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(aiResponse);

            List<ApplicationQuestion> result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, true);

            assertThat(result).hasSize(2);
            assertThat(q1.getGeneratedAnswer()).isEqualTo("새 답변1");
            assertThat(q2.getGeneratedAnswer()).isEqualTo("새 답변2");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<QuestionInput>> captor = ArgumentCaptor.forClass(List.class);
            verify(payloadBuilder).build(eq("백엔드 개발자"), eq("카카오"), captor.capture(), anyList());
            assertThat(captor.getValue()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Document 필터링")
    class DocumentFiltering {

        @Test
        @DisplayName("extractStatus가 SUCCESS인 Document의 텍스트만 수집한다")
        void filters_success_documents_only() throws Exception {
            ApplicationQuestion q = createQuestion(1, "질문1", null, null, null, null);

            Document successDoc = createDocument(DocumentExtractStatus.SUCCESS, "추출된 텍스트");
            Document failedDoc = createDocument(DocumentExtractStatus.FAILED, null);
            Document pendingDoc = createDocument(DocumentExtractStatus.PENDING, null);

            ApplicationSourceDocument binding1 = createBinding(successDoc);
            ApplicationSourceDocument binding2 = createBinding(failedDoc);
            ApplicationSourceDocument binding3 = createBinding(pendingDoc);

            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(q));
            given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
                .willReturn(List.of(binding1, binding2, binding3));
            given(payloadBuilder.build(anyString(), anyString(), anyList(), anyList()))
                .willReturn("{}");

            JsonNode aiResponse = objectMapper.readTree("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "답변" }
                  ]
                }
                """);
            given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(aiResponse);

            selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> docCaptor = ArgumentCaptor.forClass(List.class);
            verify(payloadBuilder).build(anyString(), anyString(), anyList(), docCaptor.capture());
            assertThat(docCaptor.getValue()).containsExactly("추출된 텍스트");
        }
    }

    @Nested
    @DisplayName("AI 응답 매칭")
    class ResponseMapping {

        @Test
        @DisplayName("AI 응답에서 매칭되지 않는 questionOrder는 무시된다")
        void unmatched_order_ignored() throws Exception {
            ApplicationQuestion q = createQuestion(1, "질문1", null, null, null, null);

            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.of(application));
            given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(q));
            given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
                .willReturn(List.of());
            given(payloadBuilder.build(anyString(), anyString(), anyList(), anyList()))
                .willReturn("{}");

            JsonNode aiResponse = objectMapper.readTree("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "답변1" },
                    { "questionOrder": 999, "answerText": "유령 답변" }
                  ]
                }
                """);
            given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(aiResponse);

            List<ApplicationQuestion> result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result).hasSize(1);
            assertThat(q.getGeneratedAnswer()).isEqualTo("답변1");
        }
    }

    // --- 헬퍼 메서드 ---

    private ApplicationQuestion createQuestion(int order, String text, String generatedAnswer,
                                               ApplicationToneOption tone, ApplicationLengthOption length,
                                               String emphasisPoint) {
        return ApplicationQuestion.builder()
            .questionOrder(order)
            .questionText(text)
            .generatedAnswer(generatedAnswer)
            .toneOption(tone)
            .lengthOption(length)
            .emphasisPoint(emphasisPoint)
            .build();
    }

    private Document createDocument(DocumentExtractStatus status, String extractedText) {
        return mock(Document.class, invocation -> {
            if (invocation.getMethod().getName().equals("getExtractStatus")) return status;
            if (invocation.getMethod().getName().equals("getExtractedText")) return extractedText;
            return null;
        });
    }

    private ApplicationSourceDocument createBinding(Document document) {
        return mock(ApplicationSourceDocument.class, invocation -> {
            if (invocation.getMethod().getName().equals("getDocument")) return document;
            return null;
        });
    }
}
