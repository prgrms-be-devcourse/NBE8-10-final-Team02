package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * behavioral-questions.json 전용 파서.
 *
 * JSON 구조:
 * [
 *   {
 *     "id": "B_001",
 *     "category": "basics",
 *     "variations": [
 *       { "tone": "standard", "question": "..." },
 *       { "tone": "pressure", "question": "..." }
 *     ],
 *     "ai_prompt_guide": "..."
 *   }
 * ]
 *
 * 저장 단위: variation 1개 = DB row 1개
 *   title   = "{id} [{tone}]"  예) B_001 [standard]
 *   content = question + "\n\n[AI Guide]\n" + ai_prompt_guide
 *   autoTags = KeywordTagExtractor(question + category) + "behavioral"
 */
public class JsonBehavioralParser implements KnowledgeParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<ParsedItem> parse(String rawContent, KnowledgeSource source) {
        List<ParsedItem> items = new ArrayList<>();

        JsonNode root;
        try {
            root = MAPPER.readTree(rawContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("behavioral-questions.json 파싱 실패: " + e.getMessage(), e);
        }

        if (!root.isArray()) {
            throw new IllegalArgumentException("behavioral-questions.json 최상위가 배열이어야 합니다.");
        }

        for (JsonNode entry : root) {
            String id              = entry.path("id").asText("");
            String category        = entry.path("category").asText("");
            String aiPromptGuide   = entry.path("ai_prompt_guide").asText("");
            JsonNode variations    = entry.path("variations");

            if (id.isBlank() || !variations.isArray()) continue;

            for (JsonNode variation : variations) {
                String tone     = variation.path("tone").asText("");
                String question = variation.path("question").asText("").trim();

                if (tone.isBlank() || question.isBlank()) continue;

                String title   = id + " [" + tone + "]";
                String content = buildContent(question, aiPromptGuide);

                List<String> autoTags = new ArrayList<>(
                        KeywordTagExtractor.extract(question + " " + category));
                if (!autoTags.contains("behavioral")) {
                    autoTags.add("behavioral");
                }

                items.add(new ParsedItem(title, content, autoTags));
            }
        }

        return items;
    }

    private String buildContent(String question, String aiPromptGuide) {
        if (aiPromptGuide.isBlank()) return question;
        return question + "\n\n[AI Guide]\n" + aiPromptGuide;
    }
}
