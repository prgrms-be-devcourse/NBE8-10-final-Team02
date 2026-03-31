package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;

import java.util.ArrayList;
import java.util.List;

/**
 * ## / ### 헤딩 기준으로 마크다운을 파싱한다.
 * 제목 = 헤딩 텍스트, 내용 = 다음 헤딩 전까지의 본문.
 */
public class MarkdownHeadingParser implements KnowledgeParser {

    private static final int MIN_CONTENT_LENGTH = 50;
    private static final String[] REFERENCE_KEYWORDS =
            {"참고", "references", "더 읽을거리", "관련 자료", "출처", "reference", "further reading"};

    @Override
    public List<ParsedItem> parse(String rawContent, KnowledgeSource source) {
        String preprocessed = preprocess(rawContent);
        List<ParsedItem> items = new ArrayList<>();

        String[] lines = preprocessed.split("\n");
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                if (currentTitle != null) {
                    flush(items, currentTitle, currentContent.toString().trim());
                }
                currentTitle = line.replaceFirst("^#{2,3}\\s+", "").trim();
                currentContent = new StringBuilder();
            } else if (currentTitle != null) {
                currentContent.append(line).append("\n");
            }
        }

        if (currentTitle != null) {
            flush(items, currentTitle, currentContent.toString().trim());
        }

        return items;
    }

    private void flush(List<ParsedItem> items, String title, String content) {
        if (title.isBlank() || content.length() < MIN_CONTENT_LENGTH) return;
        List<String> tags = KeywordTagExtractor.extract(title + " " + content);
        items.add(new ParsedItem(title, content, tags));
    }

    private String preprocess(String raw) {
        // 이미지 제거
        String result = raw.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
        // 인라인 링크: [텍스트](url) → 텍스트
        result = result.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
        // 참고자료 섹션 제거
        result = removeReferenceSections(result);
        return result;
    }

    private String removeReferenceSections(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        boolean skip = false;

        for (String line : lines) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                String lower = line.toLowerCase();
                skip = false;
                for (String kw : REFERENCE_KEYWORDS) {
                    if (lower.contains(kw)) {
                        skip = true;
                        break;
                    }
                }
            }
            if (!skip) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }
}
