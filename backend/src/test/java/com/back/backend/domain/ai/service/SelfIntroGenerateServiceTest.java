package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder;
import com.back.backend.domain.ai.service.SelfIntroGenerateService.GenerateResult;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.service.ApplicationStatusService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SelfIntroGenerateServiceTest {

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 10L;
    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Mock
    private ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;

    @Mock
    private AiPipeline aiPipeline;

    @Mock
    private ApplicationStatusService applicationStatusService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SelfIntroGenerateService selfIntroGenerateService;

    @BeforeEach
    void setUp() {
        selfIntroGenerateService = new SelfIntroGenerateService(
            applicationRepository,
            applicationQuestionRepository,
            sourceDocumentBindingRepository,
            applicationStatusService,
            new SelfIntroPayloadBuilder(),
            aiPipeline
        );
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
            givenApplicationExists();
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
        @DisplayName("generatedAnswer가 없는 문항만 AI 호출 대상이 된다")
        void skips_already_generated() throws Exception {
            Application application = givenApplicationExists();
            ApplicationQuestion answered = question(1, "질문1", "기존 답변",
                ApplicationToneOption.FORMAL, ApplicationLengthOption.MEDIUM, null);
            ApplicationQuestion unanswered = question(2, "질문2", null,
                ApplicationToneOption.BALANCED, ApplicationLengthOption.SHORT, "프로젝트 강조");

            givenQuestions(answered, unanswered);
            givenNoDocuments();
            givenAiResponse("""
                { "answers": [{ "questionOrder": 2, "answerText": "AI 생성 답변" }] }
                """);

            GenerateResult result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result.generatedCount()).isEqualTo(1);
            assertThat(result.allQuestions()).hasSize(2);
            assertThat(answered.getGeneratedAnswer()).isEqualTo("기존 답변");
            assertThat(unanswered.getGeneratedAnswer()).isEqualTo("AI 생성 답변");
            verify(applicationStatusService).syncStatus(application);
        }

        @Test
        @DisplayName("모든 문항에 generatedAnswer가 있으면 AI 호출 없이 반환한다")
        void all_already_generated() {
            ApplicationQuestion answered = question(1, "질문1", "기존 답변", null, null, null);

            givenApplicationExists();
            givenQuestions(answered);

            GenerateResult result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result.generatedCount()).isEqualTo(0);
            assertThat(result.allQuestions()).hasSize(1);
            assertThat(answered.getGeneratedAnswer()).isEqualTo("기존 답변");
            verify(aiPipeline, never()).execute(anyString(), anyString());
            verifyNoInteractions(applicationStatusService);
        }
    }

    @Nested
    @DisplayName("regenerate=true")
    class RegenerateTrue {

        @Test
        @DisplayName("기존 답변이 있어도 전체 문항을 AI 호출 대상으로 포함한다")
        void regenerates_all() throws Exception {
            ApplicationQuestion q1 = question(1, "질문1", "기존 답변",
                ApplicationToneOption.FORMAL, ApplicationLengthOption.LONG, null);
            ApplicationQuestion q2 = question(2, "질문2", null, null, null, null);

            givenApplicationExists();
            givenQuestions(q1, q2);
            givenNoDocuments();
            givenAiResponse("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "새 답변1" },
                    { "questionOrder": 2, "answerText": "새 답변2" }
                  ]
                }
                """);

            GenerateResult result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, true);

            assertThat(result.generatedCount()).isEqualTo(2);
            assertThat(q1.getGeneratedAnswer()).isEqualTo("새 답변1");
            assertThat(q2.getGeneratedAnswer()).isEqualTo("새 답변2");
        }

        @Test
        @DisplayName("재생성 시 editedAnswer가 null로 초기화된다")
        void regenerate_clears_editedAnswer() throws Exception {
            ApplicationQuestion q = ApplicationQuestion.builder()
                .questionOrder(1)
                .questionText("질문1")
                .generatedAnswer("이전 답변")
                .editedAnswer("사용자가 수정한 답변")
                .build();

            givenApplicationExists();
            givenQuestions(q);
            givenNoDocuments();
            givenAiResponse("""
                { "answers": [{ "questionOrder": 1, "answerText": "새 답변" }] }
                """);

            selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, true);

            assertThat(q.getGeneratedAnswer()).isEqualTo("새 답변");
            assertThat(q.getEditedAnswer()).isNull();
        }
    }

    @Nested
    @DisplayName("Document 필터링")
    class DocumentFiltering {

        @Test
        @DisplayName("extractStatus가 SUCCESS인 Document의 텍스트만 AI에 전달된다")
        void filters_success_documents_only() throws Exception {
            ApplicationQuestion q = question(1, "질문1", null, null, null, null);

            givenApplicationExists();
            givenQuestions(q);
            givenDocuments(
                binding(document(DocumentExtractStatus.SUCCESS, "추출된 텍스트")),
                binding(document(DocumentExtractStatus.FAILED, null)),
                binding(document(DocumentExtractStatus.PENDING, null))
            );
            givenAiResponse("""
                { "answers": [{ "questionOrder": 1, "answerText": "답변" }] }
                """);

            GenerateResult result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result.generatedCount()).isEqualTo(1);
            assertThat(q.getGeneratedAnswer()).isEqualTo("답변");
            verify(aiPipeline).execute(eq(TEMPLATE_ID), anyString());
        }
    }

    @Nested
    @DisplayName("AI 응답 매칭")
    class ResponseMapping {

        @Test
        @DisplayName("AI 응답에서 매칭되지 않는 questionOrder는 무시된다")
        void unmatched_order_ignored() throws Exception {
            ApplicationQuestion q = question(1, "질문1", null, null, null, null);

            givenApplicationExists();
            givenQuestions(q);
            givenNoDocuments();
            givenAiResponse("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "답변1" },
                    { "questionOrder": 999, "answerText": "유령 답변" }
                  ]
                }
                """);

            GenerateResult result = selfIntroGenerateService.generate(USER_ID, APPLICATION_ID, false);

            assertThat(result.allQuestions()).hasSize(1);
            assertThat(q.getGeneratedAnswer()).isEqualTo("답변1");
        }
    }

    // --- given 헬퍼 ---

    private Application givenApplicationExists() {
        Application application = application();
        given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
            .willReturn(Optional.of(application));
        return application;
    }

    private void givenQuestions(ApplicationQuestion... questions) {
        given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
            .willReturn(List.of(questions));
    }

    private void givenNoDocuments() {
        given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
            .willReturn(List.of());
    }

    private void givenDocuments(ApplicationSourceDocument... bindings) {
        given(sourceDocumentBindingRepository.findAllByApplicationId(APPLICATION_ID))
            .willReturn(List.of(bindings));
    }

    private void givenAiResponse(String json) throws Exception {
        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
            .willReturn(objectMapper.readTree(json));
    }

    // --- 엔티티 팩토리 ---

    private Application application() {
        return Application.builder()
            .jobRole("백엔드 개발자")
            .companyName("카카오")
            .status(ApplicationStatus.DRAFT)
            .build();
    }

    private ApplicationQuestion question(int order, String text, String generatedAnswer,
                                         ApplicationToneOption tone, ApplicationLengthOption length,
                                         String emphasisPoint) {
        return ApplicationQuestion.builder()
            .application(application())
            .questionOrder(order)
            .questionText(text)
            .generatedAnswer(generatedAnswer)
            .toneOption(tone)
            .lengthOption(length)
            .emphasisPoint(emphasisPoint)
            .build();
    }

    private Document document(DocumentExtractStatus status, String extractedText) {
        return Document.builder()
            .extractStatus(status)
            .extractedText(extractedText)
            .build();
    }

    private ApplicationSourceDocument binding(Document document) {
        return ApplicationSourceDocument.builder()
            .document(document)
            .build();
    }
}
