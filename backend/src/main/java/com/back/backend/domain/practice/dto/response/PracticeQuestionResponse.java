package com.back.backend.domain.practice.dto.response;

import com.back.backend.domain.practice.entity.PracticeQuestionType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PracticeQuestionResponse(
        Long knowledgeItemId,
        String title,
        String questionText,
        String questionType,
        List<PracticeTagResponse> tags
) {

    // title 패턴: "[카테고리] 주제" 형태
    private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\[([^]]+)]\\s*(.+)$");
    // title 패턴: "A vs B" 또는 "A vs B vs C" 형태
    private static final Pattern VS_PATTERN = Pattern.compile("(?i)\\bvs\\b");
    // title 패턴: "A & B" 또는 "A and B" 형태
    private static final Pattern AND_PATTERN = Pattern.compile("\\s[&]\\s|\\band\\b");

    public static PracticeQuestionResponse of(Long knowledgeItemId, String title, String content,
                                               PracticeQuestionType questionType,
                                               List<PracticeTagResponse> tags) {
        String questionText = buildQuestionText(title, content, questionType);
        return new PracticeQuestionResponse(knowledgeItemId, title, questionText, questionType.getValue(), tags);
    }

    private static String buildQuestionText(String title, String content, PracticeQuestionType type) {
        if (type == PracticeQuestionType.BEHAVIORAL) {
            int guideIdx = content.indexOf("[AI Guide]");
            return guideIdx >= 0 ? content.substring(0, guideIdx).trim() : content.trim();
        }
        return buildCsQuestionText(title, content);
    }

    /**
     * 세션 응답에서 질문 텍스트를 생성할 때 사용 (PracticeSessionResponse, PracticeSessionDetailResponse)
     */
    public static String buildCsQuestionTextFromSession(String title, String content,
                                                         PracticeQuestionType type) {
        return buildQuestionText(title, content, type);
    }

    private static String buildCsQuestionText(String title, String content) {
        // 1. 비교형: "A vs B" 또는 "A & B"
        if (VS_PATTERN.matcher(title).find() || AND_PATTERN.matcher(title).find()) {
            return title + "의 차이점을 비교하고, 각각 어떤 상황에서 사용하는지 설명해주세요.";
        }

        // 2. 대괄호 접두사: "[자료구조] 힙(Heap)" → "힙(Heap)"
        String cleanTitle = title;
        Matcher bracketMatcher = BRACKET_PREFIX.matcher(title);
        if (bracketMatcher.matches()) {
            cleanTitle = bracketMatcher.group(2).trim();
        }

        // 3. content 키워드 분석으로 질문 구체화
        String contentLower = content.toLowerCase();
        String modifier = analyzeContentKeywords(contentLower);

        return cleanTitle + modifier + " 설명해주세요.";
    }

    private static String analyzeContentKeywords(String contentLower) {
        boolean hasComplexity = contentLower.contains("시간복잡도") || contentLower.contains("o(")
                || contentLower.contains("시간 복잡도");
        boolean hasProsAndCons = (contentLower.contains("장점") && contentLower.contains("단점"))
                || contentLower.contains("장단점");
        boolean hasPrinciple = contentLower.contains("동작") || contentLower.contains("원리")
                || contentLower.contains("과정") || contentLower.contains("메커니즘");
        boolean hasImplementation = contentLower.contains("구현") || contentLower.contains("코드")
                || contentLower.contains("```");

        // 우선순위: 시간복잡도 > 장단점 > 동작원리 > 구현 > 기본
        if (hasComplexity && hasPrinciple) {
            return "의 동작 원리와 시간복잡도를 포함하여";
        }
        if (hasComplexity) {
            return "의 개념과 시간복잡도를 포함하여";
        }
        if (hasProsAndCons && hasPrinciple) {
            return "의 동작 원리와 장단점을 포함하여";
        }
        if (hasProsAndCons) {
            return "의 장단점을 포함하여";
        }
        if (hasPrinciple) {
            return "의 동작 원리를 중심으로";
        }
        if (hasImplementation) {
            return "의 개념과 구현 방법을 포함하여";
        }
        return "에 대해";
    }
}
