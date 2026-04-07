package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InterviewCompletionFollowupGenerationService {

    private static final String TEMPLATE_ID = "ai.interview.followup.complete.v1";

    private final AiPipeline aiPipeline;
    private final InterviewCompletionFollowupPayloadBuilder payloadBuilder;

    public List<CompletionFollowupDecision> generate(CompletionFollowupGenerationRequest request) {
        if (request.answeredThreads().isEmpty()) {
            return List.of();
        }

        try {
            JsonNode responseNode = aiPipeline.execute(
                    TEMPLATE_ID,
                    payloadBuilder.build(
                            request.jobRole(),
                            request.companyName(),
                            request.answeredThreads()
                    )
            );
            return mapGeneratedFollowups(responseNode, request.allowedParentQuestionOrders());
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
                    "AI 마지막 보완 질문 생성 중 오류가 발생했습니다."
            );
        }
    }

    private List<CompletionFollowupDecision> mapGeneratedFollowups(
            JsonNode responseNode,
            Set<Integer> allowedParentQuestionOrders
    ) {
        JsonNode followUpQuestions = responseNode.path("followUpQuestions");
        if (followUpQuestions.isMissingNode() || !followUpQuestions.isArray() || followUpQuestions.isEmpty()) {
            return List.of();
        }

        List<CompletionFollowupDecision> decisions = new ArrayList<>();
        Set<Integer> acceptedParentQuestionOrders = new LinkedHashSet<>();
        for (JsonNode followUpQuestion : followUpQuestions) {
            int parentQuestionOrder = followUpQuestion.path("parentQuestionOrder").asInt(-1);
            if (!allowedParentQuestionOrders.contains(parentQuestionOrder)) {
                continue;
            }
            if (!acceptedParentQuestionOrders.add(parentQuestionOrder)) {
                continue;
            }

            FollowupQuestionDraft followupDraft = new FollowupQuestionDraft(
                    parseQuestionType(followUpQuestion.path("questionType").asText()),
                    parseDifficultyLevel(followUpQuestion.path("difficultyLevel").asText()),
                    followUpQuestion.path("questionText").asText()
            );
            decisions.add(new CompletionFollowupDecision(parentQuestionOrder, followupDraft));
        }
        return decisions;
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

    public record CompletionFollowupGenerationRequest(
            String jobRole,
            String companyName,
            java.util.List<InterviewCompletionFollowupPayloadBuilder.AnsweredThread> answeredThreads
    ) {
        public Set<Integer> allowedParentQuestionOrders() {
            Set<Integer> allowed = new LinkedHashSet<>();
            for (InterviewCompletionFollowupPayloadBuilder.AnsweredThread answeredThread : answeredThreads) {
                allowed.add(answeredThread.tailQuestionOrder());
            }
            return allowed;
        }
    }

    public record CompletionFollowupDecision(
            int parentQuestionOrder,
            FollowupQuestionDraft followupDraft
    ) {
    }
}
