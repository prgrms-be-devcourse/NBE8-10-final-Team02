package com.back.backend.domain.practice.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PracticeEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PracticeEvaluationService.class);

    private static final String CS_TEMPLATE_ID = "ai.practice.evaluate.cs.v1";
    private static final String BEHAVIORAL_TEMPLATE_ID = "ai.practice.evaluate.behavioral.v1";

    private final AiPipeline aiPipeline;
    private final FeedbackTagRepository feedbackTagRepository;

    public PracticeEvaluationService(AiPipeline aiPipeline, FeedbackTagRepository feedbackTagRepository) {
        this.aiPipeline = aiPipeline;
        this.feedbackTagRepository = feedbackTagRepository;
    }

    public EvaluationResult evaluate(KnowledgeItem knowledgeItem, PracticeQuestionType questionType,
                                     String answerText) {
        String templateId = questionType == PracticeQuestionType.BEHAVIORAL
                ? BEHAVIORAL_TEMPLATE_ID : CS_TEMPLATE_ID;

        List<FeedbackTag> tagMaster = feedbackTagRepository.findAll();
        String payload = buildPayload(knowledgeItem, questionType, answerText, tagMaster);

        log.info("문제은행 평가 요청: templateId={}, questionType={}, itemId={}",
                templateId, questionType.getValue(), knowledgeItem.getId());

        JsonNode result = aiPipeline.execute(templateId, payload);
        return mapResult(result);
    }

    private String buildPayload(KnowledgeItem item, PracticeQuestionType questionType,
                                String answerText, List<FeedbackTag> tagMaster) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // 질문 정보
        sb.append("  \"question\": {\n");
        sb.append("    \"title\": ").append(jsonString(item.getTitle())).append(",\n");

        if (questionType == PracticeQuestionType.BEHAVIORAL) {
            // 인성 질문: content에서 question과 ai_prompt_guide를 분리
            String content = item.getContent();
            int guideIdx = content.indexOf("[AI Guide]");
            if (guideIdx >= 0) {
                String questionPart = content.substring(0, guideIdx).trim();
                String guidePart = content.substring(guideIdx + "[AI Guide]".length()).trim();
                sb.append("    \"questionText\": ").append(jsonString(questionPart)).append(",\n");
                sb.append("    \"aiPromptGuide\": ").append(jsonString(guidePart)).append("\n");
            } else {
                sb.append("    \"questionText\": ").append(jsonString(content)).append("\n");
            }
        } else {
            // CS 질문: content를 참고 자료로 제공
            sb.append("    \"referenceContent\": ").append(jsonString(item.getContent())).append("\n");
        }

        sb.append("  },\n");

        // 답변
        sb.append("  \"answer\": ").append(jsonString(answerText)).append(",\n");

        // 태그 마스터
        sb.append("  \"tagMaster\": [\n");
        for (int i = 0; i < tagMaster.size(); i++) {
            FeedbackTag tag = tagMaster.get(i);
            sb.append("    {\"id\": ").append(tag.getId())
              .append(", \"name\": ").append(jsonString(tag.getTagName()))
              .append(", \"category\": ").append(jsonString(tag.getTagCategory().getValue()))
              .append("}");
            if (i < tagMaster.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}");
        return sb.toString();
    }

    private EvaluationResult mapResult(JsonNode result) {
        int score = result.get("score").asInt();
        String feedback = result.get("feedback").asText();
        String modelAnswer = result.get("modelAnswer").asText();

        List<String> tagNames = new ArrayList<>();
        JsonNode tagNamesNode = result.get("tagNames");
        if (tagNamesNode != null && tagNamesNode.isArray()) {
            for (JsonNode tagNode : tagNamesNode) {
                tagNames.add(tagNode.asText());
            }
        }

        return new EvaluationResult(score, feedback, modelAnswer, tagNames);
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    public record EvaluationResult(
            int score,
            String feedback,
            String modelAnswer,
            List<String> tagNames
    ) {}
}
