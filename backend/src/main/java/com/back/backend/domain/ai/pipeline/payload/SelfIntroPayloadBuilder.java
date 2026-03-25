package com.back.backend.domain.ai.pipeline.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SelfIntroPayloadBuilder {

    private final ObjectMapper objectMapper;

    public SelfIntroPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record QuestionInput(
        int questionOrder,
        String questionText,
        String toneOption,    // nullable
        String lengthOption,  // nullable
        String emphasisPoint  // nullable
    ) {
    }

    public record CommitInput(
        String repoName,
        String commitMessage
    ) {
    }

    /**
     * @param jobRole       직무 (필수)
     * @param companyName   회사명 (nullable — 없으면 범용 답변 생성)
     * @param questions     생성 대상 문항 목록
     * @param documentTexts Document.extractedText 목록 (extractStatus=SUCCESS만)
     * @param commits       GithubCommit (userCommit=true만), repoName + commitMessage
     * @return AiPipeline.execute() 에 전달할 JSON payload 문자열
     */
    public String build(
        String jobRole,
        String companyName,
        List<QuestionInput> questions,
        List<String> documentTexts,
        List<CommitInput> commits
    ) {
        Objects.requireNonNull(jobRole, "jobRole must not be null");
        Objects.requireNonNull(questions, "questions must not be null");
        Objects.requireNonNull(documentTexts, "documentTexts must not be null");
        Objects.requireNonNull(commits, "commits must not be null");

        ObjectNode root = objectMapper.createObjectNode();

        root.put("jobRole", jobRole);
        if (companyName != null && !companyName.isBlank()) {
            root.put("companyName", companyName);
        }

        buildQuestionList(root, questions);
        buildPortfolioEvidence(root, documentTexts, commits);
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

    private void buildPortfolioEvidence(ObjectNode root, List<String> documentTexts, List<CommitInput> commits) {
        ArrayNode evidence = root.putArray("portfolioEvidence");

        // 문서 → 각각 독립 증거 항목
        int docIndex = 1;
        for (String extractedText : documentTexts) {
            ObjectNode ev = evidence.addObject();
            ev.put("projectKey", "doc_" + docIndex);
            ev.put("summary", extractedText);
            ev.putArray("signals");
            ev.putArray("evidenceBullets");
            ev.put("confidence", "medium");
            docIndex++;
        }

        // 커밋 → repo 단위로 그룹화
        Map<String, List<String>> commitsByRepo = commits.stream()
            .collect(Collectors.groupingBy(
                CommitInput::repoName,
                LinkedHashMap::new,
                Collectors.mapping(CommitInput::commitMessage, Collectors.toList())
            ));

        for (Map.Entry<String, List<String>> entry : commitsByRepo.entrySet()) {
            ObjectNode ev = evidence.addObject();
            ev.put("projectKey", "repo_" + entry.getKey().replaceAll("[^a-zA-Z0-9_]", "_"));
            ev.put("projectName", entry.getKey());
            ev.put("summary", "GitHub commits from " + entry.getKey());
            ev.putArray("signals");
            ArrayNode bullets = ev.putArray("evidenceBullets");
            entry.getValue().forEach(bullets::add);
            ev.put("confidence", "high");
        }
    }

    private void buildWritingConstraints(ObjectNode root) {
        ObjectNode constraints = root.putObject("writingConstraints");
        constraints.put("forbidMadeUpMetrics", true);
        constraints.put("language", "ko");
        constraints.put("preferStarStructure", true);

        ObjectNode lengthPolicy = constraints.putObject("lengthPolicy");
        ObjectNode shortPolicy = lengthPolicy.putObject("short");
        shortPolicy.put("targetChars", 500);
        shortPolicy.put("hardMaxChars", 700);
        ObjectNode mediumPolicy = lengthPolicy.putObject("medium");
        mediumPolicy.put("targetChars", 900);
        mediumPolicy.put("hardMaxChars", 1200);
        ObjectNode longPolicy = lengthPolicy.putObject("long");
        longPolicy.put("targetChars", 1400);
        longPolicy.put("hardMaxChars", 1800);
    }
}
