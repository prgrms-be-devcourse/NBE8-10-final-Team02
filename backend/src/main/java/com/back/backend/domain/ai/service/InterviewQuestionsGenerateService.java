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
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    @Transactional
    public InterviewQuestionSet generate(
        long userId,
        long applicationId,
        String title,
        int questionCount,
        DifficultyLevel difficultyLevel,
        List<String> questionTypes
    ) {
        User user = userRepository.findById(userId)
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

        List<String> documentTexts = sourceDocumentBindingRepository
            .findAllByApplicationId(applicationId).stream()
            .map(ApplicationSourceDocument::getDocument)
            .filter(doc -> doc.getExtractStatus() == DocumentExtractStatus.SUCCESS)
            .map(Document::getExtractedText)
            .toList();

        String payload = payloadBuilder.build(
            application.getJobRole(),
            application.getCompanyName(),
            selfIntroQnAs,
            documentTexts,
            questionCount,
            difficultyLevel.getValue(),
            questionTypes
        );

        JsonNode responseNode = aiPipeline.execute(TEMPLATE_ID, payload);

        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
            .user(user)
            .application(application)
            .title(title)
            .questionCount(questionCount)
            .difficultyLevel(difficultyLevel)
            .questionTypes(questionTypes.toArray(String[]::new))
            .build();

        questionSetRepository.save(questionSet);

        Map<Integer, ApplicationQuestion> appQuestionByOrder = appQuestions.stream()
            .collect(Collectors.toMap(ApplicationQuestion::getQuestionOrder, Function.identity()));

        JsonNode questions = responseNode.get("questions");
        for (JsonNode q : questions) {
            int questionOrder = q.get("questionOrder").asInt();
            String questionType = q.get("questionType").asText();
            String qDifficultyLevel = q.get("difficultyLevel").asText();
            String questionText = q.get("questionText").asText();

            ApplicationQuestion sourceAppQuestion = null;
            if (q.hasNonNull("sourceApplicationQuestionOrder")) {
                sourceAppQuestion = appQuestionByOrder.get(
                    q.get("sourceApplicationQuestionOrder").asInt()
                );
            }

            InterviewQuestion interviewQuestion = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(questionOrder)
                .questionType(parseQuestionType(questionType))
                .difficultyLevel(parseDifficultyLevel(qDifficultyLevel))
                .questionText(questionText)
                .sourceApplicationQuestion(sourceAppQuestion)
                .build();

            questionRepository.save(interviewQuestion);
        }

        questionSet.changeQuestionCount(questions.size());

        return questionSet;
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestionSet> getQuestionSets(long userId) {
        return questionSetRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public InterviewQuestionSet getQuestionSet(long userId, long questionSetId) {
        return questionSetRepository.findByIdAndUserId(questionSetId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.INTERVIEW_QUESTION_SET_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "질문 세트를 찾을 수 없습니다."
            ));
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestion> getQuestions(long questionSetId) {
        return questionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSetId);
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
}
