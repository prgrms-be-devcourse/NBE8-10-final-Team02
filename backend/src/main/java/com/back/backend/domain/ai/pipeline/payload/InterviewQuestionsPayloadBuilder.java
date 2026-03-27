package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class InterviewQuestionsPayloadBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record SelfIntroQnA(
        int questionOrder,
        String questionText,
        String generatedAnswer
    ) {
    }

    /**
     * @param jobRole          직무 (필수)
     * @param companyName      회사명 (nullable)
     * @param selfIntroQnAs    자소서 Q&A 목록 (questionText + generatedAnswer)
     * @param documentTexts    Document.extractedText 목록 (PII 마스킹 완료, extractStatus=SUCCESS만)
     * @param questionCount    생성할 질문 수 (1~20)
     * @param difficultyLevel  난이도 (easy|medium|hard)
     * @param questionTypes    질문 유형 목록
     * @return AiPipeline.execute()에 전달할 JSON payload 문자열
     */
    public String build(
        String jobRole,
        String companyName,
        List<SelfIntroQnA> selfIntroQnAs,
        List<String> documentTexts,
        int questionCount,
        String difficultyLevel,
        List<String> questionTypes
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(selfIntroQnAs, "selfIntroQnAs must not be null");
        Objects.requireNonNull(documentTexts, "documentTexts must not be null");
        Objects.requireNonNull(questionTypes, "questionTypes must not be null");

        ObjectNode root = objectMapper.createObjectNode();

        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }
        root.put("preferredQuestionCount", questionCount);
        root.put("difficultyLevel", difficultyLevel);

        buildQuestionTypes(root, questionTypes);
        buildSelfIntroContext(root, selfIntroQnAs);
        buildPortfolioEvidence(root, documentTexts);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("InterviewQuestions payload 직렬화 실패", e);
        }
    }

    private void buildQuestionTypes(ObjectNode root, List<String> questionTypes) {
        ArrayNode types = root.putArray("questionTypes");
        for (String type : questionTypes) {
            types.add(type);
        }
    }

    private void buildSelfIntroContext(ObjectNode root, List<SelfIntroQnA> selfIntroQnAs) {
        ArrayNode context = root.putArray("selfIntroQnA");
        for (SelfIntroQnA qna : selfIntroQnAs) {
            ObjectNode node = context.addObject();
            node.put("questionOrder", qna.questionOrder());
            node.put("questionText", qna.questionText());
            if (qna.generatedAnswer() != null) {
                node.put("generatedAnswer", qna.generatedAnswer());
            }
        }
    }

    private void buildPortfolioEvidence(ObjectNode root, List<String> documentTexts) {
        ArrayNode evidence = root.putArray("portfolioEvidence");

        int docIndex = 1;
        for (String extractedText : documentTexts) {
            ObjectNode ev = evidence.addObject();
            ev.put("projectKey", "doc_" + docIndex);
            ev.put("summary", extractedText);
            docIndex++;
        }
    }
}
