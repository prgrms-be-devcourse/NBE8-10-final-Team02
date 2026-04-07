package com.back.backend.domain.github.metadata;

import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * GitHub Issue/PR/Review 본문 텍스트 정제 유틸리티.
 * 설계 근거: docs/personal-design/github-metadata-pipeline.md §7.2
 *
 * <h3>적용 순서</h3>
 * <pre>
 * 1. strip()          — 코드 블록·인라인 코드·스택트레이스 제거
 * 2. removeTemplateNoise() — PR 템플릿 보일러플레이트 제거
 * 3. truncateAtSentence() — Issue/Review: 문장 경계 절삭
 *    또는 extractPrBody() — PR: BreakIterator → 스코어링 → greedy 조립
 * </pre>
 */
@Component
public class TextSanitizer {

    // ── 노이즈 제거 패턴 ──────────────────────────────────────────────────
    /** 코드 펜스 블록 (multiline) → [code] */
    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    /** 인라인 코드 → [code] */
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\n]+`");
    /** 스택트레이스 라인 */
    private static final Pattern STACKTRACE_LINE =
            Pattern.compile("\\s+at [\\w$.]+\\([\\w.]+:\\d+\\)\\n?");

    /** PR 템플릿 섹션 헤더 제거 */
    private static final List<Pattern> TEMPLATE_NOISE = List.of(
            Pattern.compile("(?m)^#{1,3}\\s*(변경\\s*사항|Changes?|What.*changed?).*$"),
            Pattern.compile("(?m)^#{1,3}\\s*(테스트\\s*방법|How to test|Test Plan).*$"),
            Pattern.compile("(?m)^#{1,3}\\s*(체크\\s*리스트|Checklist).*$"),
            Pattern.compile("(?m)^-\\s*\\[[ xX]\\].*$"),                         // 체크박스
            Pattern.compile("(?m)^>\\s*(관련\\s*(이슈|Issue)|Resolves|Closes|Fixes)\\s*#?\\d*.*$")
    );

    // ── PR body 스코어링 키워드 (§7.2.4) ────────────────────────────────
    private static final Set<String> INTERVIEW_KEYWORDS = Set.of(
            "동시성", "concurrency", "트랜잭션", "transaction", "데드락", "deadlock",
            "성능", "performance", "최적화", "optimization", "캐시", "cache",
            "인덱스", "index", "보안", "security", "인증", "authentication",
            "리팩터링", "refactor", "설계", "architecture", "분산", "distributed"
    );

    // ─────────────────────────────────────────────────────────────────────

    /**
     * 코드 블록·인라인 코드·스택트레이스를 제거한다.
     * 코드 블록은 [code]로 치환 (AI가 "여기에 코드 변경이 있었음"을 인식하도록).
     */
    public String strip(String text) {
        if (text == null || text.isBlank()) return "";
        String result = CODE_BLOCK.matcher(text).replaceAll("[code]");
        result = INLINE_CODE.matcher(result).replaceAll("[code]");
        result = STACKTRACE_LINE.matcher(result).replaceAll("");
        return result;
    }

    /**
     * PR 템플릿 보일러플레이트를 제거한다.
     * (섹션 헤더, 체크박스 항목, 이슈 번호 참조)
     */
    public String removeTemplateNoise(String text) {
        if (text == null || text.isBlank()) return "";
        String result = text;
        for (Pattern p : TEMPLATE_NOISE) {
            result = p.matcher(result).replaceAll("");
        }
        return result.strip();
    }

    /**
     * Issue/Review body 문장 경계 절삭 (최대 maxLen 문자).
     *
     * <p>마침표 뒤 공백·줄바꿈, 한국어 종결어미("다. "), 물음표, 느낌표 중 가장 늦은 위치를 탐색.
     * 70% 미만 지점에서 문장 끝을 찾으면 hard cut fallback.
     *
     * <p>Issue: maxLen=500, Review: maxLen=300
     */
    public String truncateAtSentence(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        String sub = text.substring(0, maxLen);

        int cut = IntStream.of(
                sub.lastIndexOf(". "),
                sub.lastIndexOf(".\n"),
                sub.lastIndexOf("다. "),
                sub.lastIndexOf("다.\n"),
                sub.lastIndexOf('?'),
                sub.lastIndexOf('!')
        ).max().orElse(-1);

        return (cut > maxLen * 0.7)
                ? sub.substring(0, cut + 1) + " [truncated]"
                : sub + "... [truncated]";
    }

    /**
     * PR body 3단계 extractive 스마트 발췌.
     *
     * <ol>
     *   <li>코드 블록 제거 → BreakIterator 문장 분할</li>
     *   <li>키워드 Decaying Factor + head/tail +5 스코어링</li>
     *   <li>예산 내 greedy 선택 → 원문 순서 재조립 → [...] 표시</li>
     * </ol>
     *
     * @param text   노이즈 제거 완료 텍스트 (strip + removeTemplateNoise 적용 후)
     * @param maxLen 출력 최대 문자 수 (보통 500)
     */
    public String extractPrBody(String text, int maxLen) {
        if (text == null || text.isBlank()) return "";
        if (text.length() <= maxLen) return text;

        // 1단계: 문장 분할 (코드 블록은 이미 strip에서 제거됨)
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) return text.substring(0, Math.min(text.length(), maxLen));

        int total = sentences.size();

        // 2단계: 문장별 스코어링
        int[] scores = new int[total];
        for (int i = 0; i < total; i++) {
            String s = sentences.get(i).toLowerCase();
            // 키워드 Decaying Factor
            scores[i] += keywordScore(s);
            // head(앞 3문장) / tail(뒤 3문장) 보너스
            if (i < 3 || i >= total - 3) scores[i] += 5;
        }

        // 3단계: 고득점 greedy 선택
        Integer[] indices = IntStream.range(0, total).boxed().toArray(Integer[]::new);
        Arrays.sort(indices, (a, b) -> {
            if (scores[b] != scores[a]) return scores[b] - scores[a];
            // 동점: head/tail 가까운 쪽 우선
            return Math.min(a, total - 1 - a) - Math.min(b, total - 1 - b);
        });

        int sentenceMaxLen = (int) (maxLen * 0.6); // 단일 문장 독점 방지
        Set<Integer> selected = new LinkedHashSet<>();
        int usedLen = 0;
        for (int idx : indices) {
            String s = sentences.get(idx);
            int sLen = Math.min(s.length(), sentenceMaxLen);
            if (usedLen + sLen > maxLen) continue;
            selected.add(idx);
            usedLen += sLen;
        }

        // 원문 순서 재조립
        List<Integer> ordered = selected.stream().sorted().collect(Collectors.toList());
        StringBuilder result = new StringBuilder();
        int prev = -1;
        for (int idx : ordered) {
            if (prev != -1 && idx > prev + 1) result.append(" [...] ");
            String s = sentences.get(idx);
            result.append(s.length() > sentenceMaxLen ? s.substring(0, sentenceMaxLen) + "..." : s);
            prev = idx;
        }
        return result.toString();
    }

    /** BreakIterator 기반 문장 분할 (한국어+영어 혼재 텍스트) */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator bi = BreakIterator.getSentenceInstance(Locale.KOREAN);
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            String s = text.substring(start, end).strip();
            if (!s.isEmpty()) sentences.add(s);
        }
        return sentences;
    }

    /**
     * 키워드 Decaying Factor 점수.
     * 첫 등장: base점, 두 번째: base×0.5, 세 번째: base×0.25 ...
     * 키워드별 독립 계산, 합산.
     */
    private int keywordScore(String sentence) {
        Map<String, Double> accumulated = new HashMap<>();
        for (String kw : INTERVIEW_KEYWORDS) {
            int count = countOccurrences(sentence, kw);
            if (count == 0) continue;
            // 10 × (1 - 0.5^n) / 0.5
            double score = 10.0 * (1 - Math.pow(0.5, count)) / 0.5;
            accumulated.put(kw, score);
        }
        return (int) accumulated.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }
}
