package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class InterviewCompletionFollowupPayloadBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(
            String jobRole,
            String companyName,
            List<AnsweredThread> answeredThreads
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(answeredThreads, "answeredThreads must not be null");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }

        ArrayNode threadsNode = root.putArray("answeredThreads");
        for (AnsweredThread answeredThread : answeredThreads) {
            Objects.requireNonNull(answeredThread, "answeredThread must not be null");
            Objects.requireNonNull(answeredThread.rootQuestion(), "rootQuestion must not be null");
            Objects.requireNonNull(answeredThread.rootAnswer(), "rootAnswer must not be null");

            ObjectNode threadNode = threadsNode.addObject();
            threadNode.put("tailQuestionOrder", answeredThread.tailQuestionOrder());
            writeQuestionNode(threadNode.putObject("rootQuestion"), answeredThread.rootQuestion());
            writeAnswerNode(threadNode.putObject("rootAnswer"), answeredThread.rootAnswer());

            if (answeredThread.runtimeRuleSummary() != null) {
                writeRuntimeRuleSummaryNode(
                        threadNode.putObject("runtimeRuleSummary"),
                        answeredThread.runtimeRuleSummary()
                );
            }
            if (answeredThread.runtimeFollowupQuestion() != null) {
                writeQuestionNode(
                        threadNode.putObject("runtimeFollowupQuestion"),
                        answeredThread.runtimeFollowupQuestion()
                );
            }
            if (answeredThread.runtimeFollowupAnswer() != null) {
                writeAnswerNode(
                        threadNode.putObject("runtimeFollowupAnswer"),
                        answeredThread.runtimeFollowupAnswer()
                );
            }
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("InterviewCompletionFollowup payload 직렬화 실패", exception);
        }
    }

    private void writeQuestionNode(ObjectNode node, ThreadQuestion question) {
        node.put("questionOrder", question.questionOrder());
        node.put("questionType", question.questionType());
        node.put("questionText", question.questionText());
        node.put("difficultyLevel", question.difficultyLevel());
    }

    private void writeAnswerNode(ObjectNode node, ThreadAnswer answer) {
        if (answer.answerText() != null) {
            node.put("answerText", answer.answerText());
        } else {
            node.putNull("answerText");
        }
        node.put("isSkipped", answer.isSkipped());
    }

    private void writeRuntimeRuleSummaryNode(ObjectNode node, RuntimeRuleSummary runtimeRuleSummary) {
        node.put("finalAction", runtimeRuleSummary.finalAction());
        if (runtimeRuleSummary.primaryGap() != null) {
            node.put("primaryGap", runtimeRuleSummary.primaryGap());
        } else {
            node.putNull("primaryGap");
        }
        if (runtimeRuleSummary.secondaryGap() != null) {
            node.put("secondaryGap", runtimeRuleSummary.secondaryGap());
        } else {
            node.putNull("secondaryGap");
        }

        ArrayNode candidateNode = node.putArray("candidateQuestionTypes");
        for (String candidateQuestionType : runtimeRuleSummary.candidateQuestionTypes()) {
            candidateNode.add(candidateQuestionType);
        }
    }

    public record AnsweredThread(
            int tailQuestionOrder,
            ThreadQuestion rootQuestion,
            ThreadAnswer rootAnswer,
            RuntimeRuleSummary runtimeRuleSummary,
            ThreadQuestion runtimeFollowupQuestion,
            ThreadAnswer runtimeFollowupAnswer
    ) {
    }

    public record ThreadQuestion(
            int questionOrder,
            String questionType,
            String questionText,
            String difficultyLevel
    ) {
    }

    public record ThreadAnswer(
            String answerText,
            boolean isSkipped
    ) {
    }

    public record RuntimeRuleSummary(
            String finalAction,
            String primaryGap,
            String secondaryGap,
            List<String> candidateQuestionTypes
    ) {
        public RuntimeRuleSummary {
            candidateQuestionTypes = candidateQuestionTypes == null ? List.of() : List.copyOf(candidateQuestionTypes);
        }
    }
}
