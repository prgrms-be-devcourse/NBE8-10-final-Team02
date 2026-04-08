package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewQuestionsPayloadBuilder;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.dto.response.QuestionSetSummaryResponse;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.knowledge.repository.KnowledgeTagRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionsGenerateServiceTest {

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 10L;
    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";
    private static final int QUESTION_COUNT = 3;
    private static final DifficultyLevel DIFFICULTY_LEVEL = DifficultyLevel.MEDIUM;
    private static final List<String> QUESTION_TYPES = List.of("technical_cs", "project", "behavioral");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Mock
    private ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;

    @Mock
    private InterviewQuestionSetRepository questionSetRepository;

    @Mock
    private InterviewQuestionRepository questionRepository;

    @Mock
    private AiPipeline aiPipeline;

    @Mock
    private KnowledgeTagRepository knowledgeTagRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InterviewQuestionsGenerateService service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate.execute()가 실제 콜백을 실행하도록 스텁
        // lenient: getQuestionSet() 같은 @Transactional 메서드는 TransactionTemplate을 사용하지 않음
        org.mockito.Mockito.lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(invocation -> invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                .doInTransaction(null));

        service = new InterviewQuestionsGenerateService(
            userRepository,
            applicationRepository,
            applicationQuestionRepository,
            sourceDocumentBindingRepository,
            questionSetRepository,
            questionRepository,
            new InterviewQuestionsPayloadBuilder(),
            aiPipeline,
            knowledgeTagRepository,
            transactionTemplate
        );
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("User가 존재하지 않으면 USER_NOT_FOUND 예외를 던진다")
        void user_not_found() {
            given(userRepository.findById(USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(
                USER_ID, APPLICATION_ID, "제목", QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES
            ))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("Application이 존재하지 않으면 APPLICATION_NOT_FOUND 예외를 던진다")
        void application_not_found() {
            givenUserExists();
            given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(
                USER_ID, APPLICATION_ID, "제목", QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES
            ))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("질문 세트 생성")
    class GenerateQuestionSet {

        @Test
        @DisplayName("AI 응답의 질문들이 InterviewQuestion으로 저장된다")
        void saves_questions_from_ai_response() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions(
                appQuestion(1, "지원 동기를 작성해주세요.", "저는 백엔드 개발자로서...")
            );
            givenNoDocuments();
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "technical_cs",
                      "difficultyLevel": "medium",
                      "questionText": "Spring의 DI 컨테이너가 Bean을 관리하는 방식을 설명해주세요."
                    },
                    {
                      "questionOrder": 2,
                      "questionType": "project",
                      "difficultyLevel": "hard",
                      "questionText": "프로젝트에서 성능 병목을 어떻게 해결하셨나요?",
                      "sourceApplicationQuestionOrder": 1
                    },
                    {
                      "questionOrder": 3,
                      "questionType": "behavioral",
                      "difficultyLevel": "easy",
                      "questionText": "팀 내 의견 충돌을 어떻게 해결하셨나요?"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            QuestionSetSummaryResponse result = service.generate(
                USER_ID, APPLICATION_ID, "면접 질문 세트", QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES
            );

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("면접 질문 세트");
            assertThat(result.difficultyLevel()).isEqualTo("medium");
            assertThat(result.questionCount()).isEqualTo(3);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<InterviewQuestion>> captor = ArgumentCaptor.forClass(List.class);
            verify(questionRepository).saveAll(captor.capture());

            List<InterviewQuestion> saved = captor.getValue();
            assertThat(saved).hasSize(3);
            assertThat(saved.get(0).getQuestionType()).isEqualTo(InterviewQuestionType.TECHNICAL_CS);
            assertThat(saved.get(0).getQuestionText()).isEqualTo("Spring의 DI 컨테이너가 Bean을 관리하는 방식을 설명해주세요.");
            assertThat(saved.get(1).getQuestionType()).isEqualTo(InterviewQuestionType.PROJECT);
            assertThat(saved.get(1).getDifficultyLevel()).isEqualTo(DifficultyLevel.HARD);
            assertThat(saved.get(1).getSourceApplicationQuestion()).isNotNull();
            assertThat(saved.get(2).getQuestionType()).isEqualTo(InterviewQuestionType.BEHAVIORAL);
        }

        @Test
        @DisplayName("AI가 요청한 수와 다른 수의 질문을 생성하면 실제 수로 갱신된다")
        void updates_question_count_to_actual() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions();
            givenNoDocuments();
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "technical_cs",
                      "difficultyLevel": "medium",
                      "questionText": "질문1"
                    },
                    {
                      "questionOrder": 2,
                      "questionType": "project",
                      "difficultyLevel": "medium",
                      "questionText": "질문2"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            QuestionSetSummaryResponse result = service.generate(
                USER_ID, APPLICATION_ID, null, QUESTION_COUNT, DIFFICULTY_LEVEL, QUESTION_TYPES
            );

            assertThat(result.questionCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("자소서 Q&A 필터링")
    class SelfIntroFiltering {

        @Test
        @DisplayName("generatedAnswer가 있는 문항만 AI 컨텍스트로 전달된다")
        void only_answered_questions_included() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions(
                appQuestion(1, "지원 동기", "답변 있음"),
                appQuestion(2, "장단점", null)
            );
            givenNoDocuments();
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "experience",
                      "difficultyLevel": "medium",
                      "questionText": "질문"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            service.generate(
                USER_ID, APPLICATION_ID, null, 1, DIFFICULTY_LEVEL, QUESTION_TYPES
            );

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiPipeline).execute(eq(TEMPLATE_ID), payloadCaptor.capture());

            JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
            JsonNode selfIntroQnA = payload.get("selfIntroQnA");
            assertThat(selfIntroQnA).hasSize(1);
            assertThat(selfIntroQnA.get(0).get("questionText").asText()).isEqualTo("지원 동기");
        }
    }

    @Nested
    @DisplayName("Document 필터링")
    class DocumentFiltering {

        @Test
        @DisplayName("extractStatus가 SUCCESS인 Document의 텍스트만 AI에 전달된다")
        void filters_success_documents_only() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions();
            givenDocuments(
                binding(document(DocumentExtractStatus.SUCCESS, "추출된 텍스트")),
                binding(document(DocumentExtractStatus.FAILED, null)),
                binding(document(DocumentExtractStatus.PENDING, null))
            );
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "technical_cs",
                      "difficultyLevel": "easy",
                      "questionText": "질문"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            service.generate(
                USER_ID, APPLICATION_ID, null, 1, DIFFICULTY_LEVEL, QUESTION_TYPES
            );

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiPipeline).execute(eq(TEMPLATE_ID), payloadCaptor.capture());

            JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
            JsonNode evidence = payload.get("portfolioEvidence");
            assertThat(evidence).hasSize(1);
            assertThat(evidence.get(0).get("summary").asText()).isEqualTo("추출된 텍스트");
        }
    }

    @Nested
    @DisplayName("AI 응답 파싱 실패")
    class ResponseParsingFailure {

        @Test
        @DisplayName("알 수 없는 questionType이면 IllegalArgumentException을 던진다")
        void unknown_question_type() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions();
            givenNoDocuments();
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "unknown_type",
                      "difficultyLevel": "medium",
                      "questionText": "질문"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            assertThatThrownBy(() -> service.generate(
                USER_ID, APPLICATION_ID, null, 1, DIFFICULTY_LEVEL, QUESTION_TYPES
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_type");
        }

        @Test
        @DisplayName("알 수 없는 difficultyLevel이면 IllegalArgumentException을 던진다")
        void unknown_difficulty_level() throws Exception {
            givenUserExists();
            givenApplicationExists();
            givenAppQuestions();
            givenNoDocuments();
            givenAiResponse("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "technical_cs",
                      "difficultyLevel": "impossible",
                      "questionText": "질문"
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            assertThatThrownBy(() -> service.generate(
                USER_ID, APPLICATION_ID, null, 1, DIFFICULTY_LEVEL, QUESTION_TYPES
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("impossible");
        }
    }

    @Nested
    @DisplayName("조회")
    class QueryMethods {

        @Test
        @DisplayName("존재하지 않는 질문 세트를 조회하면 INTERVIEW_QUESTION_SET_NOT_FOUND 예외를 던진다")
        void question_set_not_found() {
            given(questionSetRepository.findByIdAndUserId(999L, USER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getQuestionSet(USER_ID, 999L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INTERVIEW_QUESTION_SET_NOT_FOUND));
        }
    }

    // --- given 헬퍼 ---

    private void givenUserExists() {
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.of(User.builder().build()));
    }

    private void givenApplicationExists() {
        given(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID))
            .willReturn(Optional.of(application()));
    }

    private void givenAppQuestions(ApplicationQuestion... questions) {
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
            .build();
    }

    private ApplicationQuestion appQuestion(int order, String text, String generatedAnswer) {
        return ApplicationQuestion.builder()
            .questionOrder(order)
            .questionText(text)
            .generatedAnswer(generatedAnswer)
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
