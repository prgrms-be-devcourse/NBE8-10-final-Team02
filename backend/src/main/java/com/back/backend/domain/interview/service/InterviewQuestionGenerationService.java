package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewQuestionPayloadBuilder;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.service.ApplicationStatusService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewQuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionGenerationService.class);
    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";
    private static final int MAX_INVALID_RESULT_ATTEMPTS = 3;

    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;
    private final ApplicationStatusService applicationStatusService;
    private final InterviewQuestionPayloadBuilder interviewQuestionPayloadBuilder;
    private final AiPipeline aiPipeline;

    public GeneratedQuestionSet generate(
            long userId,
            long applicationId,
            int questionCount,
            DifficultyLevel difficultyLevel,
            List<InterviewQuestionType> questionTypes,
            String requestedTitle
    ) {
        Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.APPLICATION_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "지원 준비를 찾을 수 없습니다."
                ));

        if (!applicationStatusService.isReady(application)) {
            throw new ServiceException(
                    ErrorCode.APPLICATION_STATUS_CONFLICT,
                    HttpStatus.CONFLICT,
                    "면접 질문 생성을 위해 지원 준비를 먼저 완료해주세요."
            );
        }

        List<ApplicationQuestion> applicationQuestions =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);
        Map<Integer, ApplicationQuestion> questionByOrder = new LinkedHashMap<>();
        List<InterviewQuestionPayloadBuilder.ApplicationQuestionInput> payloadQuestions = new ArrayList<>();

        for (ApplicationQuestion applicationQuestion : applicationQuestions) {
            String finalAnswerText = resolveFinalAnswerText(applicationQuestion);
            if (finalAnswerText == null) {
                throw new ServiceException(
                        ErrorCode.APPLICATION_STATUS_CONFLICT,
                        HttpStatus.CONFLICT,
                        "면접 질문 생성을 위해 지원 준비를 먼저 완료해주세요."
                );
            }

            questionByOrder.put(applicationQuestion.getQuestionOrder(), applicationQuestion);
            payloadQuestions.add(new InterviewQuestionPayloadBuilder.ApplicationQuestionInput(
                    applicationQuestion.getQuestionOrder(),
                    applicationQuestion.getQuestionText(),
                    finalAnswerText
            ));
        }

        List<String> documentTexts = applicationSourceDocumentBindingRepository.findAllByApplicationId(applicationId).stream()
                .map(binding -> binding.getDocument())
                .filter(document -> document.getExtractStatus() == DocumentExtractStatus.SUCCESS)
                .map(Document::getExtractedText)
                .filter(text -> text != null && !text.isBlank())
                .toList();

        String payload = interviewQuestionPayloadBuilder.build(
                application.getJobRole(),
                application.getCompanyName(),
                questionCount,
                difficultyLevel.getValue(),
                questionTypes.stream()
                        .map(InterviewQuestionType::getValue)
                        .toList(),
                payloadQuestions,
                documentTexts
        );

        for (int attempt = 1; attempt <= MAX_INVALID_RESULT_ATTEMPTS; attempt++) {
            try {
                List<GeneratedQuestion> generatedQuestions =
                        mapGeneratedQuestions(aiPipeline.execute(TEMPLATE_ID, payload), questionCount, questionByOrder);

                return new GeneratedQuestionSet(
                        applicationId,
                        resolveTitle(requestedTitle, application.getCompanyName(), application.getJobRole()),
                        difficultyLevel,
                        questionTypes,
                        generatedQuestions
                );
            } catch (InvalidGeneratedQuestionSetException exception) {
                log.warn("Interview question generation returned invalid result (attempt={}/{}): {}",
                        attempt, MAX_INVALID_RESULT_ATTEMPTS, exception.getMessage());
            } catch (AiClientException exception) {
                throw new ServiceException(
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                        true
                );
            } catch (ServiceException exception) {
                if (exception.getErrorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
                    throw invalidResult();
                }
                throw exception;
            } catch (RuntimeException exception) {
                throw generationFailed();
            }
        }

        throw invalidResult();
    }

    private List<GeneratedQuestion> mapGeneratedQuestions(
            JsonNode responseNode,
            int expectedQuestionCount,
            Map<Integer, ApplicationQuestion> questionByOrder
    ) {
        JsonNode questionsNode = responseNode.path("questions");
        if (!questionsNode.isArray() || questionsNode.size() != expectedQuestionCount) {
            throw new InvalidGeneratedQuestionSetException("generated question count mismatch");
        }

        List<GeneratedQuestion> generatedQuestions = new ArrayList<>();
        LinkedHashSet<Integer> generatedOrders = new LinkedHashSet<>();

        for (JsonNode questionNode : questionsNode) {
            int questionOrder = questionNode.path("questionOrder").asInt(-1);
            if (questionOrder < 1 || !generatedOrders.add(questionOrder)) {
                throw new InvalidGeneratedQuestionSetException("invalid questionOrder");
            }

            String questionText = normalizeRequiredQuestionText(questionNode.path("questionText").asText(null));
            InterviewQuestionType questionType = parseGeneratedEnum(
                    questionNode.path("questionType").asText(null),
                    InterviewQuestionType.class
            );
            DifficultyLevel generatedDifficultyLevel = parseGeneratedEnum(
                    questionNode.path("difficultyLevel").asText(null),
                    DifficultyLevel.class
            );

            Integer sourceApplicationQuestionOrder = parseNullableOrder(questionNode.get("sourceApplicationQuestionOrder"));
            if (sourceApplicationQuestionOrder != null && !questionByOrder.containsKey(sourceApplicationQuestionOrder)) {
                throw new InvalidGeneratedQuestionSetException("unknown sourceApplicationQuestionOrder");
            }

            Integer parentQuestionOrder = parseNullableOrder(questionNode.get("parentQuestionOrder"));
            generatedQuestions.add(new GeneratedQuestion(
                    questionOrder,
                    questionType,
                    generatedDifficultyLevel,
                    questionText,
                    sourceApplicationQuestionOrder,
                    parentQuestionOrder
            ));
        }

        validateGeneratedQuestionRelations(generatedQuestions);
        return generatedQuestions.stream()
                .sorted((left, right) -> Integer.compare(left.questionOrder(), right.questionOrder()))
                .toList();
    }

    private void validateGeneratedQuestionRelations(List<GeneratedQuestion> generatedQuestions) {
        LinkedHashSet<Integer> questionOrders = generatedQuestions.stream()
                .map(GeneratedQuestion::questionOrder)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        for (GeneratedQuestion generatedQuestion : generatedQuestions) {
            Integer parentQuestionOrder = generatedQuestion.parentQuestionOrder();
            boolean isFollowUp = generatedQuestion.questionType() == InterviewQuestionType.FOLLOW_UP;

            if (isFollowUp && parentQuestionOrder == null) {
                throw new InvalidGeneratedQuestionSetException("follow_up question must reference a parent");
            }
            if (!isFollowUp && parentQuestionOrder != null) {
                throw new InvalidGeneratedQuestionSetException("non follow_up question cannot reference a parent");
            }
            if (parentQuestionOrder != null) {
                if (!questionOrders.contains(parentQuestionOrder)) {
                    throw new InvalidGeneratedQuestionSetException("unknown parentQuestionOrder");
                }
                if (parentQuestionOrder >= generatedQuestion.questionOrder()) {
                    throw new InvalidGeneratedQuestionSetException("parentQuestionOrder must reference an earlier question");
                }
            }
        }
    }

    private String resolveFinalAnswerText(ApplicationQuestion applicationQuestion) {
        String editedAnswer = normalizeOptionalText(applicationQuestion.getEditedAnswer());
        if (editedAnswer != null) {
            return editedAnswer;
        }
        return normalizeOptionalText(applicationQuestion.getGeneratedAnswer());
    }

    private String resolveTitle(String requestedTitle, String companyName, String jobRole) {
        String normalizedTitle = normalizeOptionalText(requestedTitle);
        if (normalizedTitle != null) {
            return normalizedTitle;
        }

        String normalizedCompanyName = normalizeOptionalText(companyName);
        if (normalizedCompanyName != null) {
            return normalizedCompanyName + " " + jobRole + " 예상 질문 세트";
        }
        return jobRole + " 예상 질문 세트";
    }

    private String normalizeRequiredQuestionText(String questionText) {
        String normalizedQuestionText = normalizeOptionalText(questionText);
        if (normalizedQuestionText == null) {
            throw new InvalidGeneratedQuestionSetException("questionText must not be blank");
        }
        return normalizedQuestionText;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer parseNullableOrder(JsonNode orderNode) {
        if (orderNode == null || orderNode.isNull()) {
            return null;
        }
        int value = orderNode.asInt(-1);
        if (value < 1) {
            throw new InvalidGeneratedQuestionSetException("order reference must be positive");
        }
        return value;
    }

    private <E extends Enum<E> & StringCodeEnum> E parseGeneratedEnum(String rawValue, Class<E> enumType) {
        String normalizedValue = normalizeOptionalText(rawValue);
        if (normalizedValue == null) {
            throw new InvalidGeneratedQuestionSetException("generated enum value is missing");
        }

        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> candidate.getValue().equals(normalizedValue))
                .findFirst()
                .orElseThrow(() -> new InvalidGeneratedQuestionSetException("unsupported generated enum value"));
    }

    private ServiceException invalidResult() {
        return new ServiceException(
                ErrorCode.INTERVIEW_QUESTION_RESULT_INVALID,
                HttpStatus.BAD_GATEWAY,
                "면접 질문 생성 결과가 완전하지 않습니다.",
                true
        );
    }

    private ServiceException generationFailed() {
        return new ServiceException(
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                HttpStatus.BAD_GATEWAY,
                "면접 질문 생성 중 오류가 발생했습니다.",
                true
        );
    }

    public record GeneratedQuestionSet(
            long applicationId,
            String title,
            DifficultyLevel difficultyLevel,
            List<InterviewQuestionType> questionTypes,
            List<GeneratedQuestion> questions
    ) {
    }

    public record GeneratedQuestion(
            int questionOrder,
            InterviewQuestionType questionType,
            DifficultyLevel difficultyLevel,
            String questionText,
            Integer sourceApplicationQuestionOrder,
            Integer parentQuestionOrder
    ) {
    }

    private static final class InvalidGeneratedQuestionSetException extends RuntimeException {

        private InvalidGeneratedQuestionSetException(String message) {
            super(message);
        }
    }
}
