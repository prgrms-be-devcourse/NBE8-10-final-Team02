package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewFollowupPayloadBuilder;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class InterviewFollowupGenerationService {

    private static final String TEMPLATE_ID = "ai.interview.followup.generate.v1";

    private final AiPipeline aiPipeline;
    private final InterviewFollowupPayloadBuilder payloadBuilder;

    public GeneratedInterviewFollowup generate(FollowupGenerationRequest request) {
        if (request.currentAnswer().isSkipped() || request.followUpDepth() >= request.maxDepth()) {
            return null;
        }

        try {
            JsonNode responseNode = aiPipeline.execute(
                    TEMPLATE_ID,
                    payloadBuilder.build(
                            request.jobRole(),
                            request.companyName(),
                            request.currentQuestion(),
                            request.currentAnswer(),
                            request.followUpDepth(),
                            request.maxDepth()
                    )
            );
            return mapGeneratedFollowup(responseNode, request.currentQuestion().questionOrder());
        } catch (AiClientException exception) {
            throw new ServiceException(
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                    true
            );
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 꼬리 질문 생성 중 오류가 발생했습니다."
            );
        }
    }

    private GeneratedInterviewFollowup mapGeneratedFollowup(JsonNode responseNode, int expectedParentQuestionOrder) {
        JsonNode followUpQuestion = responseNode.path("followUpQuestion");
        List<String> qualityFlags = responseNode.path("qualityFlags").isArray()
                ? StreamSupport.stream(responseNode.path("qualityFlags").spliterator(), false)
                        .map(JsonNode::asText)
                        .toList()
                : List.of();

        if (followUpQuestion.isMissingNode() || followUpQuestion.isNull()) {
            return null;
        }

        int parentQuestionOrder = followUpQuestion.path("parentQuestionOrder").asInt(-1);
        if (parentQuestionOrder != expectedParentQuestionOrder) {
            throw new ServiceException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 꼬리 질문 응답의 부모 질문 순번이 일치하지 않습니다."
            );
        }

        return new GeneratedInterviewFollowup(
                parseQuestionType(followUpQuestion.path("questionType").asText()),
                parseDifficultyLevel(followUpQuestion.path("difficultyLevel").asText()),
                followUpQuestion.path("questionText").asText().trim(),
                parentQuestionOrder,
                qualityFlags
        );
    }

    private InterviewQuestionType parseQuestionType(String value) {
        return Arrays.stream(InterviewQuestionType.values())
                .filter(type -> type.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown questionType: " + value));
    }

    private DifficultyLevel parseDifficultyLevel(String value) {
        return Arrays.stream(DifficultyLevel.values())
                .filter(level -> level.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown difficultyLevel: " + value));
    }

    public record FollowupGenerationRequest(
            String jobRole,
            String companyName,
            InterviewFollowupPayloadBuilder.CurrentQuestion currentQuestion,
            InterviewFollowupPayloadBuilder.CurrentAnswer currentAnswer,
            int followUpDepth,
            int maxDepth
    ) {
    }

    public record GeneratedInterviewFollowup(
            InterviewQuestionType questionType,
            DifficultyLevel difficultyLevel,
            String questionText,
            int parentQuestionOrder,
            List<String> qualityFlags
    ) {
    }
}
