package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class InterviewResultGenerationService {

    private static final String EVALUATE_TEMPLATE_ID = "ai.interview.evaluate.v1";

    private final AiPipeline aiPipeline;
    private final FeedbackTagRepository feedbackTagRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeneratedInterviewResult generate(long sessionId, long questionSetId, List<InterviewAnswer> answers) {
        List<FeedbackTag> tagMaster = feedbackTagRepository.findAllByOrderByIdAsc();
        if (tagMaster.isEmpty()) {
            throw generationFailed();
        }

        try {
            JsonNode response = aiPipeline.execute(
                    EVALUATE_TEMPLATE_ID,
                    objectMapper.writeValueAsString(buildEvaluatePayload(sessionId, questionSetId, answers, tagMaster))
            );
            return mapGeneratedResult(response, answers, tagMaster);
        } catch (AiClientException exception) {
            throw new ServiceException(
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                    true
            );
        } catch (JsonProcessingException exception) {
            throw generationFailed();
        } catch (ServiceException exception) {
            if (exception.getErrorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
                throw incompleteResult();
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw generationFailed();
        }
    }

    private Map<String, Object> buildEvaluatePayload(
            long sessionId,
            long questionSetId,
            List<InterviewAnswer> answers,
            List<FeedbackTag> tagMaster
    ) {
        return Map.of(
                "sessionId", sessionId,
                "questionSetId", questionSetId,
                "tagMaster", tagMaster.stream()
                        .map(tag -> Map.of(
                                "tagId", tag.getId(),
                                "tagName", tag.getTagName(),
                                "tagCategory", tag.getTagCategory().getValue()
                        ))
                        .toList(),
                "answers", answers.stream()
                        .map(answer -> Map.of(
                                "questionOrder", answer.getAnswerOrder(),
                                "questionId", answer.getQuestion().getId(),
                                "questionType", answer.getQuestion().getQuestionType().getValue(),
                                "difficultyLevel", answer.getQuestion().getDifficultyLevel().getValue(),
                                "questionText", answer.getQuestion().getQuestionText(),
                                "answerText", answer.getAnswerText(),
                                "isSkipped", answer.isSkipped()
                        ))
                        .toList()
        );
    }

    private GeneratedInterviewResult mapGeneratedResult(
            JsonNode response,
            List<InterviewAnswer> answers,
            List<FeedbackTag> tagMaster
    ) {
        Set<Integer> expectedAnswerOrders = answers.stream()
                .map(InterviewAnswer::getAnswerOrder)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, FeedbackTag> tagsByName = tagMaster.stream()
                .collect(Collectors.toMap(FeedbackTag::getTagName, Function.identity()));

        JsonNode generatedAnswers = response.path("answers");
        if (!generatedAnswers.isArray() || generatedAnswers.size() != answers.size()) {
            throw incompleteResult();
        }

        List<GeneratedInterviewAnswerResult> generatedAnswerResults = StreamSupport.stream(generatedAnswers.spliterator(), false)
                .map(answerNode -> mapGeneratedAnswerResult(answerNode, expectedAnswerOrders, tagsByName))
                .toList();

        long distinctAnswerOrderCount = generatedAnswerResults.stream()
                .map(GeneratedInterviewAnswerResult::answerOrder)
                .distinct()
                .count();
        if (distinctAnswerOrderCount != answers.size()) {
            throw incompleteResult();
        }

        return new GeneratedInterviewResult(
                response.path("totalScore").asInt(),
                response.path("summaryFeedback").asText(),
                generatedAnswerResults
        );
    }

    private GeneratedInterviewAnswerResult mapGeneratedAnswerResult(
            JsonNode answerNode,
            Set<Integer> expectedAnswerOrders,
            Map<String, FeedbackTag> tagsByName
    ) {
        int questionOrder = answerNode.path("questionOrder").asInt(-1);
        if (!expectedAnswerOrders.contains(questionOrder)) {
            throw incompleteResult();
        }

        JsonNode tagNames = answerNode.path("tagNames");
        if (!tagNames.isArray()) {
            throw incompleteResult();
        }

        List<String> resolvedTagNames = StreamSupport.stream(tagNames.spliterator(), false)
                .map(JsonNode::asText)
                .map(String::trim)
                .filter(tagName -> !tagName.isEmpty())
                .toList();

        if (resolvedTagNames.size() != tagNames.size()) {
            throw incompleteResult();
        }

        if (resolvedTagNames.stream().anyMatch(tagName -> !tagsByName.containsKey(tagName))) {
            throw incompleteResult();
        }

        if (resolvedTagNames.stream().distinct().count() != resolvedTagNames.size()) {
            throw incompleteResult();
        }

        return new GeneratedInterviewAnswerResult(
                questionOrder,
                answerNode.path("score").asInt(),
                answerNode.path("evaluationRationale").asText(),
                resolvedTagNames
        );
    }

    private ServiceException incompleteResult() {
        return new ServiceException(
                ErrorCode.INTERVIEW_RESULT_INCOMPLETE,
                HttpStatus.BAD_GATEWAY,
                "면접 결과 생성 결과가 완전하지 않습니다.",
                true
        );
    }

    private ServiceException generationFailed() {
        return new ServiceException(
                ErrorCode.INTERVIEW_RESULT_GENERATION_FAILED,
                HttpStatus.BAD_GATEWAY,
                "면접 결과 생성 중 오류가 발생했습니다.",
                true
        );
    }

    public record GeneratedInterviewResult(
            int totalScore,
            String summaryFeedback,
            List<GeneratedInterviewAnswerResult> answers
    ) {
    }

    public record GeneratedInterviewAnswerResult(
            int answerOrder,
            int score,
            String evaluationRationale,
            List<String> tagNames
    ) {
    }
}
