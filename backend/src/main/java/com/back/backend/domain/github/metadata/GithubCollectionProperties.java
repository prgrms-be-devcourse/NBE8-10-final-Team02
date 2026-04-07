package com.back.backend.domain.github.metadata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub 메타데이터 수집 관련 설정값.
 *
 * <pre>
 * github:
 *   collection:
 *     page-size: 100      # GraphQL first 최대치
 *     max-fetch: 300      # Hard Cap (PR/Issue 각각 독립)
 *     since-days: 730     # 수집 기간 2년
 *   metadata:
 *     rate-limit:
 *       graphql-minimum-remaining: 500
 *       rest-minimum-remaining: 100
 *       check-cache-ttl-seconds: 60
 *     token-budget: 1500
 *     top-n: 5
 * </pre>
 */
@ConfigurationProperties(prefix = "github")
public class GithubCollectionProperties {

    private final Collection collection = new Collection();
    private final Metadata metadata = new Metadata();

    public Collection getCollection() { return collection; }
    public Metadata getMetadata() { return metadata; }

    public static class Collection {
        /** GraphQL first 값 (GitHub API 최대치: 100) */
        private int pageSize = 100;
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
