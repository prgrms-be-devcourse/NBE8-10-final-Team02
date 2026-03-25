package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SelfIntroPayloadBuilder {

    private static final int SHORT_TARGET_CHARS = 500;
    private static final int SHORT_MAX_CHARS = 700;
    private static final int MEDIUM_TARGET_CHARS = 900;
    private static final int MEDIUM_MAX_CHARS = 1200;
    private static final int LONG_TARGET_CHARS = 1400;
    private static final int LONG_MAX_CHARS = 1800;

    private static final String CONFIDENCE_MEDIUM = "medium";

    private final ObjectMapper objectMapper;

    public SelfIntroPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record QuestionInput(
        int questionOrder,
        String questionText,
        String toneOption,
        String lengthOption,
        String emphasisPoint
    ) {
    }

    /**
     * @param jobRole       직무 (필수)
     * @param companyName   회사명 (nullable — 없으면 범용 답변 생성)
     * @param questions     생성 대상 문항 목록
     * @param documentTexts Document.extractedText 목록 (PII 마스킹 완료 플레인텍스트, extractStatus=SUCCESS만)
     * @return AiPipeline.execute() 에 전달할 JSON payload 문자열
     */
    public String build(
        String jobRole,
        String companyName,
        List<QuestionInput> questions,
        List<String> documentTexts
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(questions, "questions must not be null");
        Objects.requireNonNull(documentTexts, "documentTexts must not be null");

        ObjectNode root = objectMapper.createObjectNode();

        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }

        buildQuestionList(root, questions);
        buildPortfolioEvidence(root, documentTexts);
        buildWritingConstraints(root);
        root.putArray("existingEditedAnswers");

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SelfIntro payload 직렬화 실패", e);
        }
    }

    private void buildQuestionList(ObjectNode root, List<QuestionInput> questions) {
        ArrayNode questionList = root.putArray("questionList");
        for (QuestionInput q : questions) {
            ObjectNode node = questionList.addObject();
            node.put("questionOrder", q.questionOrder());
            node.put("questionText", q.questionText());
            if (q.toneOption() != null) node.put("toneOption", q.toneOption());
            if (q.lengthOption() != null) node.put("lengthOption", q.lengthOption());
            if (q.emphasisPoint() != null) node.put("emphasisPoint", q.emphasisPoint());
        }
    }

    private void buildPortfolioEvidence(ObjectNode root, List<String> documentTexts) {
        ArrayNode evidence = root.putArray("portfolioEvidence");

        int docIndex = 1;
        for (String extractedText : documentTexts) {
            ObjectNode ev = evidence.addObject();
            ev.put("projectKey", "doc_" + docIndex);
            ev.put("summary", extractedText);
            ev.putArray("signals");
            ev.putArray("evidenceBullets");
            ev.put("confidence", CONFIDENCE_MEDIUM);
            docIndex++;
        }
    }

    private void buildWritingConstraints(ObjectNode root) {
        ObjectNode constraints = root.putObject("writingConstraints");
        constraints.put("forbidMadeUpMetrics", true);
        constraints.put("language", "ko");
        constraints.put("preferStarStructure", true);

        ObjectNode lengthPolicy = constraints.putObject("lengthPolicy");

        ObjectNode shortPolicy = lengthPolicy.putObject("short");
        shortPolicy.put("targetChars", SHORT_TARGET_CHARS);
        shortPolicy.put("hardMaxChars", SHORT_MAX_CHARS);

        ObjectNode mediumPolicy = lengthPolicy.putObject("medium");
        mediumPolicy.put("targetChars", MEDIUM_TARGET_CHARS);
        mediumPolicy.put("hardMaxChars", MEDIUM_MAX_CHARS);

        ObjectNode longPolicy = lengthPolicy.putObject("long");
        longPolicy.put("targetChars", LONG_TARGET_CHARS);
        longPolicy.put("hardMaxChars", LONG_MAX_CHARS);
    }
}
