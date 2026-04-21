package com.back.backend.domain.github.metadata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub 메타데이터 수집 관련 설정값.
 *
 * <pre>
 * github:
 *   collection:
 *     page-size: 50       # GraphQL first 값. 100→50 변경으로 페이지당 포인트 절감.
 *                         # labels(first:5) 제거 후 페이지당 비용: 100pts → 50pts (-50%)
 *                         # 대형 레포(300건) 기준 시간당 처리량: 2 → 8 레포 (4배 개선)
 *     max-fetch: 300      # Hard Cap (PR/Issue 각각 독립). pageSize=50 기준 최대 6페이지.
 *     since-days: 730     # 수집 기간 2년. Time-bound early-exit 기준.
 *   metadata:
 *     rate-limit:
 *       graphql-minimum-remaining: 500  # 레포 1개 최악 비용(635pts) + 여유분
 *       rest-minimum-remaining: 100
 *       check-cache-ttl-seconds: 60
 *     token-budget: 1500
 *     top-n: 5
 * </pre>
 *
 * <h3>pageSize 선택 근거</h3>
 * GitHub GraphQL은 {@code first} 값 기준으로 포인트 과금한다(실제 반환 건수 무관).
 * PR Phase 1 쿼리에 중첩 커넥션이 없으므로 페이지당 포인트 = {@code first} 값 그대로.
 * pageSize를 줄이면 페이지당 포인트는 감소하지만 페이지 수가 증가해 총 포인트는 동일해진다.
 * 단, 실제 PR 수가 pageSize 미만인 레포(소형)에서는 1페이지로 완료되므로 포인트 절약 효과가 있다.
 */
@ConfigurationProperties(prefix = "github")
public class GithubCollectionProperties {

    private final Collection collection = new Collection();
    private final Metadata metadata = new Metadata();

    public Collection getCollection() { return collection; }
    public Metadata getMetadata() { return metadata; }

    public static class Collection {
        /** GraphQL first 값 — 50으로 제한하여 GraphQL 포인트 절감 */
        private int pageSize = 50;
        /** Hard Cap — 2년 이내라도 극단적으로 많은 PR/Issue 방어 */
        private int maxFetch = 300;
        /** 수집 기간(일). today - sinceDays 이전은 early-exit */
        private int sinceDays = 730;

        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public int getMaxFetch() { return maxFetch; }
        public void setMaxFetch(int maxFetch) { this.maxFetch = maxFetch; }
        public int getSinceDays() { return sinceDays; }
        public void setSinceDays(int sinceDays) { this.sinceDays = sinceDays; }
    }

    public static class Metadata {
        private final RateLimit rateLimit = new RateLimit();
        /** GitHub Activity 섹션 토큰 예산 (1 token ≈ 4 chars) */
        private int tokenBudget = 1500;
        /** PR/Issue 각각 Impact Score 상위 N개 */
        private int topN = 5;

        public RateLimit getRateLimit() { return rateLimit; }
        public int getTokenBudget() { return tokenBudget; }
        public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }

        public static class RateLimit {
            private int graphqlMinimumRemaining = 500;
            private int restMinimumRemaining = 100;
            private int checkCacheTtlSeconds = 60;

            public int getGraphqlMinimumRemaining() { return graphqlMinimumRemaining; }
            public void setGraphqlMinimumRemaining(int v) { this.graphqlMinimumRemaining = v; }
            public int getRestMinimumRemaining() { return restMinimumRemaining; }
            public void setRestMinimumRemaining(int v) { this.restMinimumRemaining = v; }
            public int getCheckCacheTtlSeconds() { return checkCacheTtlSeconds; }
            public void setCheckCacheTtlSeconds(int v) { this.checkCacheTtlSeconds = v; }
        }
    }
}
