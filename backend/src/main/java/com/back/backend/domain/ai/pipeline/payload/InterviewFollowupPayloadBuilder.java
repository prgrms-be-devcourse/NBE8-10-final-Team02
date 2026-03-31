package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class InterviewFollowupPayloadBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(
            String jobRole,
            String companyName,
            CurrentQuestion currentQuestion,
            CurrentAnswer currentAnswer,
            int followUpDepth,
            int maxDepth
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(currentQuestion, "currentQuestion must not be null");
        Objects.requireNonNull(currentAnswer, "currentAnswer must not be null");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }

        ObjectNode questionNode = root.putObject("currentQuestion");
        questionNode.put("questionOrder", currentQuestion.questionOrder());
        questionNode.put("questionType", currentQuestion.questionType());
        questionNode.put("questionText", currentQuestion.questionText());
        questionNode.put("difficultyLevel", currentQuestion.difficultyLevel());

        ObjectNode answerNode = root.putObject("currentAnswer");
        if (currentAnswer.answerText() != null) {
            answerNode.put("answerText", currentAnswer.answerText());
        } else {
            answerNode.putNull("answerText");
        }
        answerNode.put("isSkipped", currentAnswer.isSkipped());

        root.put("followUpDepth", followUpDepth);
        root.put("maxDepth", maxDepth);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("InterviewFollowup payload 직렬화 실패", exception);
        }
    }

    public record CurrentQuestion(
            int questionOrder,
            String questionType,
            String questionText,
            String difficultyLevel
    ) {
    }

    public record CurrentAnswer(
            String answerText,
            boolean isSkipped
    ) {
    }
}
