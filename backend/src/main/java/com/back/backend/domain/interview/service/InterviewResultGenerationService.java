package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.portfolio.service.FailedJobRedisStore;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
    private static final String OVERLAY_BASE = "developer/evaluate-role/";

    private final AiPipeline aiPipeline;
    private final FeedbackTagRepository feedbackTagRepository;
    private final FailedJobRedisStore failedJobRedisStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 면접 결과를 생성하고, 실패 시 Redis 실패 로그를 기록한 뒤 예외를 다시 던진다.
     *
     * @param userId 결과를 생성할 사용자 ID (실패 로그 기록용)
     */
    public GeneratedInterviewResult generate(Long userId, long sessionId, long questionSetId, List<InterviewAnswer> answers, String jobRole) {
        try {
            return doGenerate(sessionId, questionSetId, answers, jobRole);
        } catch (ServiceException exception) {
            // ServiceException은 에러 코드가 명확하므로 해당 코드로 실패 로그 기록
            failedJobRedisStore.push(
                    userId,
                    FailedJobRedisStore.JobType.INTERVIEW_RESULT,
                    exception.getErrorCode().name(),
                    exception.getMessage() != null ? exception.getMessage() : "면접 결과 생성 실패"
            );
            throw exception;
        } catch (Exception exception) {
            failedJobRedisStore.push(
                    userId,
                    FailedJobRedisStore.JobType.INTERVIEW_RESULT,
                    ErrorCode.INTERVIEW_RESULT_GENERATION_FAILED.name(),
                    exception.getMessage() != null ? exception.getMessage() : "면접 결과 생성 중 오류가 발생했습니다."
            );
            throw generationFailed();
        }
    }

    /** generate()에서 실제 로직을 수행한다. 실패 로그 기록은 generate()가 담당한다. */
    private GeneratedInterviewResult doGenerate(long sessionId, long questionSetId, List<InterviewAnswer> answers, String jobRole) {
        List<FeedbackTag> tagMaster = feedbackTagRepository.findAllByOrderByIdAsc();
        if (tagMaster.isEmpty()) {
            throw generationFailed();
        }

        String roleOverlayFile = resolveRoleOverlay(jobRole);

        try {
            JsonNode response = aiPipeline.execute(
                    EVALUATE_TEMPLATE_ID,
                    objectMapper.writeValueAsString(buildEvaluatePayload(sessionId, questionSetId, answers, tagMaster, jobRole)),
                    roleOverlayFile
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
            List<FeedbackTag> tagMaster,
            String jobRole
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("questionSetId", questionSetId);
        payload.put("jobRole", jobRole);
        payload.put("tagMaster", tagMaster.stream()
                .map(tag -> Map.of(
                        "tagId", tag.getId(),
                        "tagName", tag.getTagName(),
                        "tagCategory", tag.getTagCategory().getValue()
                ))
                .toList());
        payload.put("answers", answers.stream()
                .map(answer -> Map.of(
                        "questionOrder", answer.getAnswerOrder(),
                        "questionId", answer.getSessionQuestion().getId(),
                        "questionType", answer.getSessionQuestion().getQuestionType().getValue(),
                        "difficultyLevel", answer.getSessionQuestion().getDifficultyLevel().getValue(),
                        "questionText", answer.getSessionQuestion().getQuestionText(),
                        "answerText", answer.getAnswerText() != null ? answer.getAnswerText() : "",
                        "isSkipped", answer.isSkipped()
                ))
                .toList());
        return payload;
    }

    String resolveRoleOverlay(String jobRole) {
        if (jobRole == null || jobRole.isBlank()) {
            return OVERLAY_BASE + "default.txt";
        }
        String normalized = jobRole.trim().toLowerCase();

        if (normalized.contains("백엔드") || normalized.contains("backend")
                || normalized.contains("서버") || normalized.contains("server")) {
            return OVERLAY_BASE + "backend.txt";
        }
        if (normalized.contains("프론트엔드") || normalized.contains("frontend")
                || normalized.contains("프론트") || normalized.contains("front")) {
            return OVERLAY_BASE + "frontend.txt";
        }
        if (normalized.contains("풀스택") || normalized.contains("fullstack")
                || normalized.contains("full-stack") || normalized.contains("full stack")) {
            return OVERLAY_BASE + "fullstack.txt";
        }
        if (normalized.contains("데브옵스") || normalized.contains("devops")
                || normalized.contains("인프라") || normalized.contains("sre")
                || normalized.contains("클라우드") || normalized.contains("cloud")) {
            return OVERLAY_BASE + "devops.txt";
        }
        if (normalized.contains("모바일") || normalized.contains("mobile")
                || normalized.contains("ios") || normalized.contains("android")
                || normalized.contains("flutter") || normalized.contains("react native")
                || normalized.contains("앱")) {
            return OVERLAY_BASE + "mobile.txt";
        }
        if (normalized.contains("데이터") || normalized.contains("data")
                || normalized.contains("ml") || normalized.contains("머신러닝")
                || normalized.contains("machine learning") || normalized.contains("ai")
                || normalized.contains("인공지능")) {
            return OVERLAY_BASE + "data.txt";
        }
        return OVERLAY_BASE + "default.txt";
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
