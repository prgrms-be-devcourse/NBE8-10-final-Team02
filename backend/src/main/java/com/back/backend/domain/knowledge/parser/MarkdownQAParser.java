package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Q: / A: 형식의 마크다운을 파싱한다.
 * Q: 로 시작하는 줄 = 제목, A: 이후 ~ 다음 Q: = 내용.
 */
public class MarkdownQAParser implements KnowledgeParser {

    @Override
    public List<ParsedItem> parse(String rawContent, KnowledgeSource source) {
        String preprocessed = preprocess(rawContent);
        List<ParsedItem> items = new ArrayList<>();

        String[] lines = preprocessed.split("\n");
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();
        boolean inAnswer = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("Q:") || trimmed.startsWith("Q. ")) {
                if (currentTitle != null && !currentContent.toString().isBlank()) {
                    flush(items, currentTitle, currentContent.toString().trim());
                }
                currentTitle = trimmed.replaceFirst("^Q[:.] ?", "").trim();
                currentContent = new StringBuilder();
                inAnswer = false;

            } else if ((trimmed.startsWith("A:") || trimmed.startsWith("A. ")) && currentTitle != null) {
                currentContent.append(trimmed.replaceFirst("^A[:.] ?", "")).append("\n");
                inAnswer = true;

            } else if (inAnswer && currentTitle != null) {
                currentContent.append(line).append("\n");
            }
        }

        if (currentTitle != null && !currentContent.toString().isBlank()) {
            flush(items, currentTitle, currentContent.toString().trim());
        }

        return items;
    }

    private void flush(List<ParsedItem> items, String title, String content) {
        if (title.isBlank()) return;
        List<String> tags = KeywordTagExtractor.extract(title + " " + content);
        items.add(new ParsedItem(title, content, tags));
    }

    private String preprocess(String raw) {
        String result = raw.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
        return result.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
    }
}
