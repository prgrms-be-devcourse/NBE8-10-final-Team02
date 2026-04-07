package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.CollectedIssue;
import com.back.backend.domain.github.service.GithubApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 지정된 repo에서 사용자가 작성한 Issue를 GraphQL 2-Phase로 수집한다.
 *
 * <h3>수집 조건</h3>
 * <ul>
 *   <li>state=CLOSED + stateReason=COMPLETED (NOT_PLANNED 제외)</li>
 *   <li>author.login == githubLogin (본인 작성만)</li>
 *   <li>bot 제외: __typename == "Bot" 또는 login.endsWith("[bot]")</li>
 *   <li>bodyText 30자 미만 → bodyExcerpt = null (title만 AI 주입)</li>
 * </ul>
 *
 * <h3>페이징 종료 조건 (이중 안전장치)</h3>
 * <ul>
 *   <li>Time-bound: closedAt < today - sinceDays (2년)</li>
 *   <li>Hard Cap: 누적 수집 건수 >= maxFetch (300)</li>
 * </ul>
 */
@Service
public class GitHubIssueCollector {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCollector.class);

    private static final String PHASE1_QUERY = """
            query($owner: String!, $name: String!, $login: String!, $first: Int!, $cursor: String) {
              repository(owner: $owner, name: $name) {
                issues(
                  filterBy: {createdBy: $login, states: [CLOSED]}
                  first: $first
                  after: $cursor
                  orderBy: {field: UPDATED_AT, direction: DESC}
                ) {
                  pageInfo { hasNextPage endCursor }
                  nodes {
                    id
                    number
                    title
                    bodyText
                    stateReason
                    closedAt
                    createdAt
                    comments { totalCount }
                    author { login __typename }
                  }
                }
              }
            }
            """;

    private static final String PHASE2_QUERY = """
            query($ids: [ID!]!) {
              nodes(ids: $ids) {
                ... on Issue {
                  number
                  title
                  bodyText
                  closedAt
                  createdAt
                }
              }
            }
            """;

    private final GithubApiClient githubApiClient;
    private final GithubCollectionProperties properties;
    private final TextSanitizer textSanitizer;

    public GitHubIssueCollector(
            GithubApiClient githubApiClient,
            GithubCollectionProperties properties,
            TextSanitizer textSanitizer
    ) {
        this.githubApiClient = githubApiClient;
        this.properties = properties;
        this.textSanitizer = textSanitizer;
    }

    /**
     * Issue 수집 진입점.
     *
     * @param owner       repo 소유자 로그인
     * @param repoName    repo 이름
     * @param githubLogin 수집 대상 사용자 로그인
     * @param accessToken OAuth 액세스 토큰
     * @return Impact Score 상위 N개 정제된 Issue 목록
     */
    public List<CollectedIssue> collect(
            String owner,
            String repoName,
            String githubLogin,
            String accessToken
    ) {
        int pageSize = properties.getCollection().getPageSize();
        int maxFetch = properties.getCollection().getMaxFetch();
        int sinceDays = properties.getCollection().getSinceDays();
        int topN = properties.getMetadata().getTopN();

        Instant since = Instant.now().minus(sinceDays, ChronoUnit.DAYS);

        // Phase 1: 경량 메타데이터 전체 수집
        List<IssuePhase1> phase1List = fetchPhase1(owner, repoName, githubLogin, accessToken,
                pageSize, maxFetch, since);

        if (phase1List.isEmpty()) return List.of();

        // Impact Score 계산 → 상위 N개 선정
        List<String> topIds = phase1List.stream()
                .sorted(Comparator.comparingDouble(this::issueScore).reversed())
                .limit(topN)
                .map(IssuePhase1::id)
                .toList();

        // Phase 2: 상위 N개 상세 조회
        return fetchPhase2(topIds, accessToken);
    }

    @SuppressWarnings("unchecked")
    private List<IssuePhase1> fetchPhase1(
            String owner, String repoName, String githubLogin,
            String accessToken, int pageSize, int maxFetch, Instant since
    ) {
        List<IssuePhase1> result = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int fetched = 0;

        while (hasNextPage && fetched < maxFetch) {
            Map<String, Object> vars = Map.of(
                    "owner", owner,
                    "name", repoName,
                    "login", githubLogin,
                    "first", pageSize,
                    "cursor", cursor != null ? cursor : ""
            );

            Map<String, Object> response = githubApiClient.executeGraphQL(PHASE1_QUERY, vars, accessToken);
            if (hasGraphQLErrors(response)) break;

            Map<String, Object> repo = getNestedMap(response, "data", "repository");
            if (repo == null) break;

            Map<String, Object> issues = (Map<String, Object>) repo.get("issues");
            if (issues == null) break;

            Map<String, Object> pageInfo = (Map<String, Object>) issues.get("pageInfo");
            hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            cursor = (String) pageInfo.get("endCursor");

            List<Map<String, Object>> nodes = (List<Map<String, Object>>) issues.get("nodes");
            if (nodes == null || nodes.isEmpty()) break;

            for (Map<String, Object> node : nodes) {
                // Time-bound: closedAt 기준 early-exit
                Instant closedAt = parseInstant(node, "closedAt");
                if (closedAt != null && closedAt.isBefore(since)) {
                    hasNextPage = false;
                    break;
                }

                // stateReason == COMPLETED 만 수집
                if (!"COMPLETED".equals(node.get("stateReason"))) continue;

                // bot 제외
                Map<String, Object> author = (Map<String, Object>) node.get("author");
                if (author != null && isBot(author)) continue;

                String id = (String) node.get("id");
                int number = toInt(node.get("number"));
                String title = (String) node.get("title");
                String bodyText = (String) node.getOrDefault("bodyText", "");
                Instant createdAt = parseInstant(node, "createdAt");
                int totalComments = 0;
                Map<String, Object> comments = (Map<String, Object>) node.get("comments");
                if (comments != null) totalComments = toInt(comments.get("totalCount"));

                result.add(new IssuePhase1(id, number, title, bodyText, closedAt, createdAt, totalComments));
                fetched++;
                if (fetched >= maxFetch) break;
            }
        }

        log.debug("IssueCollector phase1: fetched={}, repo={}/{}", fetched, "?", "?");
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CollectedIssue> fetchPhase2(List<String> ids, String accessToken) {
        if (ids.isEmpty()) return List.of();

        Map<String, Object> vars = Map.of("ids", ids);
        Map<String, Object> response = githubApiClient.executeGraphQL(PHASE2_QUERY, vars, accessToken);
        if (hasGraphQLErrors(response)) return List.of();

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) return List.of();

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.get("nodes");
        if (nodes == null) return List.of();

        List<CollectedIssue> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null) continue;
            int number = toInt(node.get("number"));
            String title = (String) node.get("title");
            String bodyText = (String) node.getOrDefault("bodyText", "");
            Instant closedAt = parseInstant(node, "closedAt");
            Instant createdAt = parseInstant(node, "createdAt");

            // 정제: 노이즈 제거 → 문장 경계 절삭 (최대 500자)
            String cleaned = textSanitizer.strip(bodyText);
            cleaned = textSanitizer.removeTemplateNoise(cleaned);
            String bodyExcerpt = cleaned.length() >= 30
                    ? textSanitizer.truncateAtSentence(cleaned, 500)
                    : null;

            result.add(new CollectedIssue(number, title, bodyExcerpt, closedAt, createdAt));
        }
        return result;
    }

    /**
     * Issue Impact Score — 단순 버전 (PR보다 가벼운 신호).
     * recency × (1 + log10(totalComments + 1))
     */
    private double issueScore(IssuePhase1 issue) {
        if (issue.closedAt() == null) return 0.0;
        double recency = recencyWeight(issue.closedAt());
        double engagement = 1.0 + Math.log10(issue.totalCommentsCount() + 1);
        return recency * engagement;
    }

    /** today=1.0, 2년전=0.0 선형 감쇠 */
    private double recencyWeight(Instant at) {
        long sinceMs = properties.getCollection().getSinceDays() * 86_400_000L;
        long ageMs = Instant.now().toEpochMilli() - at.toEpochMilli();
        return Math.max(0.0, 1.0 - (double) ageMs / sinceMs);
    }

    private boolean isBot(Map<String, Object> author) {
        String typename = (String) author.getOrDefault("__typename", "");
        String login = (String) author.getOrDefault("login", "");
        return "Bot".equals(typename) || login.endsWith("[bot]");
    }

    @SuppressWarnings("unchecked")
    private boolean hasGraphQLErrors(Map<String, Object> response) {
        List<?> errors = (List<?>) response.get("errors");
        if (errors != null && !errors.isEmpty()) {
            log.warn("GitHub GraphQL returned errors: {}", errors);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
        }
        return current instanceof Map ? (Map<String, Object>) current : null;
    }

    private Instant parseInstant(Map<String, Object> node, String key) {
        Object val = node.get(key);
        if (val == null) return null;
        try { return Instant.parse(val.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    /** Phase 1 경량 메타 (내부 전용) */
    private record IssuePhase1(
            String id, int number, String title, String bodyText,
            Instant closedAt, Instant createdAt, int totalCommentsCount
    ) {}
}
