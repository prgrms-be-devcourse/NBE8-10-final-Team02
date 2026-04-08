package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewQuestionsPayloadBuilder;
import com.back.backend.domain.ai.pipeline.payload.InterviewQuestionsPayloadBuilder.SelfIntroQnA;
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
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetDetailResponse;
import com.back.backend.domain.interview.dto.response.QuestionSetSummaryResponse;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.knowledge.entity.KnowledgeTag;
import com.back.backend.domain.knowledge.repository.KnowledgeTagRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewQuestionsGenerateService {

    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;
    private final InterviewQuestionSetRepository questionSetRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewQuestionsPayloadBuilder payloadBuilder;
    private final AiPipeline aiPipeline;
    private final KnowledgeTagRepository knowledgeTagRepository;
    private final TransactionTemplate transactionTemplate;

    public QuestionSetSummaryResponse generate(
        long userId,
        long applicationId,
        String title,
        int questionCount,
        DifficultyLevel difficultyLevel,
        List<String> questionTypes
    ) {
        // Phase 1: DB 읽기 (짧은 TX — lazy Document 접근 포함, 즉시 커밋)
        record ReadCtx(String payload) {}

        ReadCtx ctx = Objects.requireNonNull(transactionTemplate.execute(status -> {
            userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                    ErrorCode.USER_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "사용자를 찾을 수 없습니다."
                ));

            Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ServiceException(
                    ErrorCode.APPLICATION_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "지원 준비를 찾을 수 없습니다."
                ));

            List<ApplicationQuestion> appQuestions =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);

            List<SelfIntroQnA> selfIntroQnAs = appQuestions.stream()
                .filter(q -> q.getGeneratedAnswer() != null)
                .map(q -> new SelfIntroQnA(
                    q.getQuestionOrder(),
                    q.getQuestionText(),
                    q.getGeneratedAnswer()
                ))
                .toList();

            // Lazy 연관 Document 접근 — TX 안에서 수행해야 LazyInitializationException 방지
            List<String> documentTexts = sourceDocumentBindingRepository
                .findAllByApplicationId(applicationId).stream()
                .map(ApplicationSourceDocument::getDocument)
                .filter(doc -> doc.getExtractStatus() == DocumentExtractStatus.SUCCESS)
                .map(Document::getExtractedText)
                .toList();

            List<String> knowledgeTagNames = knowledgeTagRepository.findAll().stream()
                .map(KnowledgeTag::getName)
                .toList();

            String payload = payloadBuilder.build(
                application.getJobRole(),
                application.getCompanyName(),
                selfIntroQnAs,
                documentTexts,
                questionCount,
                difficultyLevel.getValue(),
                questionTypes,
                knowledgeTagNames
            );

            return new ReadCtx(payload);
        }));

        // Phase 2: AI 호출 — 트랜잭션 없음, DB 커넥션 미점유
        JsonNode responseNode = aiPipeline.execute(TEMPLATE_ID, ctx.payload());

        // Phase 3: DB 쓰기 (짧은 TX — managed 엔티티 재조회 후 저장)
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                .orElseThrow(); // Phase 1에서 이미 검증됨
            Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(); // Phase 1에서 이미 검증됨

            Map<Integer, ApplicationQuestion> appQuestionByOrder =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId)
                    .stream()
                    .collect(Collectors.toMap(ApplicationQuestion::getQuestionOrder, Function.identity()));

            Map<String, KnowledgeTag> knowledgeTagsByName = knowledgeTagRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeTag::getName, Function.identity()));

            InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(title)
                .questionCount(questionCount)
                .difficultyLevel(difficultyLevel)
                .questionTypes(questionTypes.toArray(String[]::new))
                .build();

            questionSetRepository.save(questionSet);

            JsonNode questions = responseNode.get("questions");
            List<InterviewQuestion> interviewQuestions = new ArrayList<>();
            for (JsonNode q : questions) {
                ApplicationQuestion sourceAppQuestion = null;
                if (q.hasNonNull("sourceApplicationQuestionOrder")) {
                    sourceAppQuestion = appQuestionByOrder.get(
                        q.get("sourceApplicationQuestionOrder").asInt()
                    );
                }

                InterviewQuestion interviewQuestion = InterviewQuestion.builder()
                    .questionSet(questionSet)
                    .questionOrder(q.get("questionOrder").asInt())
                    .questionType(parseQuestionType(q.get("questionType").asText()))
                    .difficultyLevel(parseDifficultyLevel(q.get("difficultyLevel").asText()))
                    .questionText(q.get("questionText").asText())
                    .sourceApplicationQuestion(sourceAppQuestion)
                    .build();

                if (q.hasNonNull("knowledgeTagNames") && q.get("knowledgeTagNames").isArray()) {
                    List<KnowledgeTag> tags = new ArrayList<>();
                    for (JsonNode tagName : q.get("knowledgeTagNames")) {
                        KnowledgeTag tag = knowledgeTagsByName.get(tagName.asText());
                        if (tag != null) tags.add(tag);
                    }
                    interviewQuestion.assignKnowledgeTags(tags);
                }

                interviewQuestions.add(interviewQuestion);
            }
            questionRepository.saveAll(interviewQuestions);

            questionSet.changeQuestionCount(questions.size());

            return toSummaryResponse(questionSet);
        }));
    }

    @Transactional(readOnly = true)
    public List<QuestionSetSummaryResponse> getQuestionSets(long userId) {
        return questionSetRepository.findAllByUserId(userId).stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public QuestionSetDetailResponse getQuestionSet(long userId, long questionSetId) {
        InterviewQuestionSet questionSet = questionSetRepository.findByIdAndUserId(questionSetId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.INTERVIEW_QUESTION_SET_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "질문 세트를 찾을 수 없습니다."
            ));

        List<InterviewQuestionResponse> questions = questionRepository
            .findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSetId).stream()
            .map(this::toQuestionResponse)
            .toList();

        return new QuestionSetDetailResponse(
            questionSet.getId(),
            questionSet.getApplication().getId(),
            questionSet.getTitle(),
            questionSet.getQuestionCount(),
            questionSet.getDifficultyLevel().getValue(),
            questionSet.getCreatedAt(),
            questions
        );
    }

    private InterviewQuestionType parseQuestionType(String value) {
        return Arrays.stream(InterviewQuestionType.values())
            .filter(e -> e.getValue().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown questionType: " + value
            ));
    }

    private DifficultyLevel parseDifficultyLevel(String value) {
        return Arrays.stream(DifficultyLevel.values())
            .filter(e -> e.getValue().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown difficultyLevel: " + value
            ));
    }

    private QuestionSetSummaryResponse toSummaryResponse(InterviewQuestionSet questionSet) {
        return new QuestionSetSummaryResponse(
            questionSet.getId(),
            questionSet.getApplication().getId(),
            questionSet.getTitle(),
            questionSet.getQuestionCount(),
            questionSet.getDifficultyLevel().getValue(),
            questionSet.getCreatedAt()
        );
    }

    private InterviewQuestionResponse toQuestionResponse(InterviewQuestion question) {
        return new InterviewQuestionResponse(
            question.getId(),
            question.getQuestionOrder(),
            question.getQuestionType().getValue(),
            question.getDifficultyLevel().getValue(),
            question.getQuestionText(),
            question.getParentQuestion() != null ? question.getParentQuestion().getId() : null,
            question.getSourceApplicationQuestion() != null
                ? question.getSourceApplicationQuestion().getId() : null
        );
    }
}
