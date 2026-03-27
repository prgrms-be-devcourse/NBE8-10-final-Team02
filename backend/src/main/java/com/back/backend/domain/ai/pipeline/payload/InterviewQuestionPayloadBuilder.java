package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class InterviewQuestionPayloadBuilder {

    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";
    private static final String TEMPLATE_VERSION = "v1";
    private static final String TASK_TYPE = "interview_question_generation";
    private static final String LOCALE = "ko-KR";
    private static final String CONFIDENCE_MEDIUM = "medium";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ApplicationQuestionInput(
            int questionOrder,
            String questionText,
            String finalAnswerText
    ) {
    }

    public String build(
            String jobRole,
            String companyName,
            int preferredQuestionCount,
            String difficultyLevel,
            List<String> questionTypes,
            List<ApplicationQuestionInput> applicationQuestions,
            List<String> documentTexts
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(difficultyLevel, "difficultyLevel must not be null");
        Objects.requireNonNull(questionTypes, "questionTypes must not be null");
        Objects.requireNonNull(applicationQuestions, "applicationQuestions must not be null");
        Objects.requireNonNull(documentTexts, "documentTexts must not be null");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("templateId", TEMPLATE_ID);
        root.put("templateVersion", TEMPLATE_VERSION);
        root.put("taskType", TASK_TYPE);
        root.put("locale", LOCALE);
        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }
        root.put("preferredQuestionCount", preferredQuestionCount);
        root.put("difficultyLevel", difficultyLevel);

        ArrayNode questionTypeArray = root.putArray("questionTypes");
        questionTypes.forEach(questionTypeArray::add);

        ArrayNode questionArray = root.putArray("applicationQuestions");
        for (ApplicationQuestionInput applicationQuestion : applicationQuestions) {
            ObjectNode questionNode = questionArray.addObject();
            questionNode.put("questionOrder", applicationQuestion.questionOrder());
            questionNode.put("questionText", applicationQuestion.questionText());
            questionNode.put("finalAnswerText", applicationQuestion.finalAnswerText());
        }

        ArrayNode evidenceArray = root.putArray("portfolioEvidence");
        for (int index = 0; index < documentTexts.size(); index++) {
            ObjectNode evidenceNode = evidenceArray.addObject();
            evidenceNode.put("projectKey", "doc_" + (index + 1));
            evidenceNode.putArray("signals");
            ArrayNode evidenceBullets = evidenceNode.putArray("evidenceBullets");
            evidenceBullets.add(documentTexts.get(index));
            evidenceNode.put("confidence", CONFIDENCE_MEDIUM);
        }

        ObjectNode questionGenerationRules = root.putObject("questionGenerationRules");
        questionGenerationRules.put("avoidQuestionCopy", true);
        questionGenerationRules.put("maxDuplicateSimilarity", 0.8);
        questionGenerationRules.put("includeCsBasicsWhenContextLow", true);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Interview question payload serialization failed", exception);
        }
    }
}
