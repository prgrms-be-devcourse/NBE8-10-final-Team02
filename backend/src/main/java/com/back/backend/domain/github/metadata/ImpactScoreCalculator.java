package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.PrPhase1Meta;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PR Phase 1 메타데이터 기반 Impact Score 계산기.
 * 설계 근거: docs/personal-design/github-metadata-pipeline.md §7.1.6
 *
 * <h3>점수 구성</h3>
 * <pre>
 * (sizeScore + prefixScore + keywordScore + bodyScore + engagement + labelBonus)
 *     × penalty × recency
 * </pre>
 */
@Component
public class ImpactScoreCalculator {

    // 면접 소재 키워드 테이블 (커밋 스코어링과 동일, ×0.6 계수 적용)
    private static final Map<String, Integer> KEYWORD_WEIGHTS = Map.ofEntries(
            Map.entry("동시성", 8), Map.entry("concurrency", 8),
            Map.entry("트랜잭션", 8), Map.entry("transaction", 8),
            Map.entry("데드락", 8), Map.entry("deadlock", 8),
            Map.entry("분산락", 8), Map.entry("distributed lock", 8),
            Map.entry("성능", 7), Map.entry("performance", 7),
            Map.entry("최적화", 7), Map.entry("optimization", 7),
            Map.entry("캐시", 6), Map.entry("cache", 6),
            Map.entry("인덱스", 6), Map.entry("index", 6),
            Map.entry("보안", 7), Map.entry("security", 7),
            Map.entry("인증", 6), Map.entry("authentication", 6),
            Map.entry("리팩터링", 5), Map.entry("refactor", 5),
            Map.entry("설계", 5), Map.entry("architecture", 5)
    );

    private static final Set<String> BOT_LOGINS = Set.of(
            "dependabot[bot]", "renovate[bot]", "github-actions[bot]", "codecov[bot]"
    );

    private final GithubCollectionProperties properties;

    public ImpactScoreCalculator(GithubCollectionProperties properties) {
        this.properties = properties;
    }

    /**
     * PR Phase 1 메타데이터로 Impact Score를 계산한다.
     *
     * @return 0.0이면 하드 제외 대상 (봇, 자동생성 극단 케이스)
     */
    public double score(PrPhase1Meta pr) {

        // ── STEP 1. 하드 제외 ──────────────────────────────
        if (isBot(pr.authorLogin(), pr.authorTypename())) return 0.0;

        int addDel = pr.additions() + pr.deletions();
        int bodyLen = pr.bodyText() == null ? 0 : pr.bodyText().length();
        int reviewCnt = pr.reviewTotalCount();

        // package-lock.json 재생성, .svg 번들 등 자동생성 극단 케이스
        if (addDel > 5000 && reviewCnt == 0) return 0.0;

        // ── STEP 2. 소프트 패널티 ──────────────────────────
        // 설명 없는 대형 PR이지만 팀이 검토하지 않은 경우
        double penalty = (addDel > 1000 && bodyLen < 50 && reviewCnt == 0) ? 0.1 : 1.0;

        // ── STEP 3. 점수 계산 ──────────────────────────────
        // sizeScore: 로그 스케일 (대형 PR 과대평가 억제, 계수 2 = PR이 커밋 합산이므로 낮춤)
        double sizeScore = Math.log10(addDel + 1) * 2;

        // prefixScore: conventional commit 형식 PR title 분류
        double prefixScore = titlePrefixScore(pr.title());

        // keywordScore: 기술 키워드 × 0.6 (PR body는 커밋 diff보다 밀도 낮음)
        double keywordScore = keywordBonus(pr.title(), pr.bodyText()) * 0.6;

        // bodyScore: body 충실도 (binary 아닌 연속값, 상한 9점)
        double bodyScore = Math.min(Math.log10(bodyLen + 1) * 3, 9);

        // engagement: totalComments(봇 포함 ×2) + review(봇 드묾 ×5)
        double engagement = pr.totalCommentsCount() * 2.0 + reviewCnt * 5.0;

        // labelBonus: 라벨 사용 비율 50% 미만 repo는 0
        double labelBonus = computeLabelBonus(pr.labels(), pr.repoLabelCoverage());

        // recency: today=1.0, 2년전=0.0
        double recency = recencyWeight(pr.mergedAt());

        return (sizeScore + prefixScore + keywordScore + bodyScore + engagement + labelBonus)
                * penalty * recency;
    }

    private boolean isBot(String login, String typename) {
        return "Bot".equals(typename)
                || BOT_LOGINS.contains(login)
                || login.endsWith("[bot]");
    }

    private double titlePrefixScore(String title) {
        if (title == null) return 0;
        String lower = title.toLowerCase();
        if (lower.startsWith("feat:") || lower.startsWith("perf:")) return 5;
        if (lower.startsWith("refactor:")) return 4;
        if (lower.startsWith("fix:") || lower.startsWith("resolve:")) return 3;
        if (lower.startsWith("chore:") || lower.startsWith("docs:")) return -10;
        return 0;
    }

    /**
     * 키워드 보너스 — Decaying Factor 방식.
     * 같은 키워드 반복: 10 + 5 + 2.5 + ... = 10 × (1 - 0.5^n) / 0.5
     * 서로 다른 키워드는 각각 독립 시작.
     */
    private double keywordBonus(String title, String body) {
        String text = ((title != null ? title : "") + " " + (body != null ? body : "")).toLowerCase();
        Map<String, Double> accumulated = new HashMap<>();

        for (Map.Entry<String, Integer> entry : KEYWORD_WEIGHTS.entrySet()) {
            String kw = entry.getKey();
            int count = countOccurrences(text, kw);
            if (count == 0) continue;
            double decaying = entry.getValue() * 2.0 * (1 - Math.pow(0.5, count));
            accumulated.put(kw, decaying);
        }
        return accumulated.values().stream().mapToDouble(Double::doubleValue).sum();
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

    private double computeLabelBonus(List<String> labels, double repoLabelCoverage) {
        // 라벨 사용 비율 50% 미만 repo는 라벨 신뢰도 없음으로 간주
        if (repoLabelCoverage < 0.5) return 0;
        int bonus = 0;
        for (String label : labels) {
            String l = label.toLowerCase();
            if (l.equals("feature") || l.equals("bug")) bonus += 20;
            else if (l.equals("chore") || l.equals("docs")) bonus -= 20;
        }
        return bonus;
    }

    /** today=1.0, 2년전=0.0 선형 감쇠 */
    private double recencyWeight(Instant mergedAt) {
        if (mergedAt == null) return 0.0;
        long sinceMs = (long) properties.getCollection().getSinceDays() * 86_400_000L;
        long ageMs = Instant.now().toEpochMilli() - mergedAt.toEpochMilli();
        return Math.max(0.0, 1.0 - (double) ageMs / sinceMs);
    }
}
