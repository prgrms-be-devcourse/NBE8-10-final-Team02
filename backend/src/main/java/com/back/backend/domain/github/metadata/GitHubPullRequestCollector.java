package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.CollectedPullRequest;
import com.back.backend.domain.github.metadata.dto.CollectedReview;
import com.back.backend.domain.github.metadata.dto.PrPhase1Meta;
import com.back.backend.domain.github.service.GithubApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 지정된 repo에서 사용자가 생성한 MERGED PR과 CHANGES_REQUESTED 리뷰를 GraphQL 2-Phase로 수집한다.
 *
 * <h3>페이징 종료 조건 (이중 안전장치)</h3>
 * <ul>
 *   <li>Time-bound: mergedAt < today - sinceDays (2년)</li>
 *   <li>Hard Cap: 누적 수집 건수 >= maxFetch (300)</li>
 * </ul>
 *
 * <h3>Phase 2 후처리</h3>
 * <ul>
 *   <li>리뷰: CHANGES_REQUESTED 만 필터</li>
 *   <li>리뷰어별 최신 1개 dedup (같은 리뷰어의 반복 CHANGES_REQUESTED 중 최신본)</li>
 * </ul>
 */
@Service
public class GitHubPullRequestCollector {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestCollector.class);

    private static final String PHASE1_QUERY = """
            query($owner: String!, $name: String!, $first: Int!, $cursor: String) {
              repository(owner: $owner, name: $name) {
                pullRequests(
                  states: [MERGED]
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
                    mergedAt
                    createdAt
                    additions
                    deletions
                    totalCommentsCount
                    author { login __typename }
                    labels(first: 5) { nodes { name } }
                    reviews { totalCount }
                  }
                }
              }
            }
            """;

    private static final String PHASE2_QUERY = """
            query($ids: [ID!]!) {
              nodes(ids: $ids) {
                ... on PullRequest {
                  number
                  title
                  bodyText
                  mergedAt
                  createdAt
                  reviews(first: 5) {
                    nodes {
                      author { login }
                      state
                      bodyText
                      submittedAt
                    }
                  }
                }
              }
            }
            """;

    private final GithubApiClient githubApiClient;
    private final GithubCollectionProperties properties;
    private final ImpactScoreCalculator impactScoreCalculator;
    private final TextSanitizer textSanitizer;

    public GitHubPullRequestCollector(
            GithubApiClient githubApiClient,
            GithubCollectionProperties properties,
            ImpactScoreCalculator impactScoreCalculator,
            TextSanitizer textSanitizer
    ) {
        this.githubApiClient = githubApiClient;
        this.properties = properties;
        this.impactScoreCalculator = impactScoreCalculator;
        this.textSanitizer = textSanitizer;
    }

    /**
     * PR 수집 진입점.
     *
     * @param owner       repo 소유자 로그인
     * @param repoName    repo 이름
     * @param githubLogin 수집 대상 사용자 로그인 (본인 PR만)
     * @param accessToken OAuth 액세스 토큰
     * @return Impact Score 상위 N개 정제된 PR 목록
     */
    public List<CollectedPullRequest> collect(
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

        // Phase 1: 경량 메타데이터 전체 수집 (본인 PR만 필터)
        List<PrPhase1Meta> phase1List = fetchPhase1(owner, repoName, githubLogin,
                accessToken, pageSize, maxFetch, since);

        if (phase1List.isEmpty()) return List.of();

        // Impact Score → 하드 제외(0.0) 후 상위 N개 ID 선정
        List<String> topIds = phase1List.stream()
                .filter(pr -> impactScoreCalculator.score(pr) > 0.0)
                .sorted(Comparator.comparingDouble(impactScoreCalculator::score).reversed())
                .limit(topN)
                .map(PrPhase1Meta::id)
                .toList();

        if (topIds.isEmpty()) return List.of();

        // Phase 2: 상위 N개 상세 조회
        return fetchPhase2(topIds, accessToken);
    }

    @SuppressWarnings("unchecked")
    private List<PrPhase1Meta> fetchPhase1(
            String owner, String repoName, String githubLogin,
            String accessToken, int pageSize, int maxFetch, Instant since
    ) {
        List<PrPhase1Meta> result = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int fetched = 0;

        // Phase 1 전체 수집 후 라벨 커버리지 계산용
        int totalPrs = 0;
        int labeledPrs = 0;

        // 1차 패스: 전체 수집 (라벨 커버리지 계산 위해 raw 저장)
        List<Map<String, Object>> rawNodes = new ArrayList<>();

        while (hasNextPage && fetched < maxFetch) {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("owner", owner);
            vars.put("name", repoName);
            vars.put("first", pageSize);
            if (cursor != null) vars.put("cursor", cursor);

            Map<String, Object> response = githubApiClient.executeGraphQL(PHASE1_QUERY, vars, accessToken);
            if (hasGraphQLErrors(response)) break;

            Map<String, Object> repo = getNestedMap(response, "data", "repository");
            if (repo == null) break;

            Map<String, Object> prs = (Map<String, Object>) repo.get("pullRequests");
            if (prs == null) break;

            Map<String, Object> pageInfo = (Map<String, Object>) prs.get("pageInfo");
            hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            cursor = (String) pageInfo.get("endCursor");

            List<Map<String, Object>> nodes = (List<Map<String, Object>>) prs.get("nodes");
            if (nodes == null || nodes.isEmpty()) break;

            for (Map<String, Object> node : nodes) {
                // Time-bound: mergedAt 기준 early-exit
                Instant mergedAt = parseInstant(node, "mergedAt");
                if (mergedAt != null && mergedAt.isBefore(since)) {
                    hasNextPage = false;
                    break;
                }

                // 본인 PR만 (author.login 필터)
                Map<String, Object> author = (Map<String, Object>) node.get("author");
                if (author == null) continue;
                String login = (String) author.getOrDefault("login", "");
                if (!githubLogin.equals(login)) continue;

                rawNodes.add(node);
                totalPrs++;
                List<Map<String, Object>> labels = extractLabelNames(node);
                if (!labels.isEmpty()) labeledPrs++;

                fetched++;
                if (fetched >= maxFetch) break;
            }
        }

        // 라벨 커버리지: 50% 이상이면 라벨 신뢰도 있음
        double coverage = totalPrs > 0 ? (double) labeledPrs / totalPrs : 0.0;

        // 2차 패스: PrPhase1Meta로 변환
        for (Map<String, Object> node : rawNodes) {
            Map<String, Object> author = (Map<String, Object>) node.get("author");
            String authorLogin = author != null ? (String) author.getOrDefault("login", "") : "";
            String authorTypename = author != null ? (String) author.getOrDefault("__typename", "User") : "User";

            List<Map<String, Object>> rawLabels = extractLabelNodes(node);
            List<String> labelNames = rawLabels.stream()
                    .map(l -> (String) l.get("name"))
                    .filter(n -> n != null)
                    .toList();

            Map<String, Object> reviews = (Map<String, Object>) node.get("reviews");
            int reviewCount = reviews != null ? toInt(reviews.get("totalCount")) : 0;

            result.add(new PrPhase1Meta(
                    (String) node.get("id"),
                    toInt(node.get("number")),
                    (String) node.get("title"),
                    (String) node.getOrDefault("bodyText", ""),
                    parseInstant(node, "mergedAt"),
                    parseInstant(node, "createdAt"),
                    toInt(node.get("additions")),
                    toInt(node.get("deletions")),
                    toInt(node.get("totalCommentsCount")),
                    authorLogin,
                    authorTypename,
                    labelNames,
                    reviewCount,
                    coverage
            ));
        }

        log.debug("PRCollector phase1: fetched={}, labeled={}/{}", fetched, labeledPrs, totalPrs);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CollectedPullRequest> fetchPhase2(List<String> ids, String accessToken) {
        if (ids.isEmpty()) return List.of();

        Map<String, Object> vars = Map.of("ids", ids);
        Map<String, Object> response = githubApiClient.executeGraphQL(PHASE2_QUERY, vars, accessToken);
        if (hasGraphQLErrors(response)) return List.of();

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) return List.of();

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.get("nodes");
        if (nodes == null) return List.of();

        List<CollectedPullRequest> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null) continue;

            int number = toInt(node.get("number"));
            String title = (String) node.get("title");
            String rawBody = (String) node.getOrDefault("bodyText", "");
            Instant mergedAt = parseInstant(node, "mergedAt");
            Instant createdAt = parseInstant(node, "createdAt");

            // PR body 정제: 노이즈 제거 → extractPrBody (3단계 스마트 발췌)
            String cleaned = textSanitizer.strip(rawBody);
            cleaned = textSanitizer.removeTemplateNoise(cleaned);
            String bodyExcerpt = cleaned.length() >= 50
                    ? textSanitizer.extractPrBody(cleaned, 500)
                    : null;

            // 리뷰: CHANGES_REQUESTED 만 + 리뷰어별 최신 1개 dedup
            List<CollectedReview> reviews = extractReviews(node);

            result.add(new CollectedPullRequest(number, title, bodyExcerpt, mergedAt, createdAt, reviews));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CollectedReview> extractReviews(Map<String, Object> prNode) {
        Map<String, Object> reviewsMap = (Map<String, Object>) prNode.get("reviews");
        if (reviewsMap == null) return List.of();
        List<Map<String, Object>> reviewNodes = (List<Map<String, Object>>) reviewsMap.get("nodes");
        if (reviewNodes == null) return List.of();

        // CHANGES_REQUESTED 만, 리뷰어별 최신 1개 dedup
        Map<String, CollectedReview> latestByReviewer = new LinkedHashMap<>();
        for (Map<String, Object> rv : reviewNodes) {
            if (!"CHANGES_REQUESTED".equals(rv.get("state"))) continue;

            Map<String, Object> rvAuthor = (Map<String, Object>) rv.get("author");
            String reviewerLogin = rvAuthor != null ? (String) rvAuthor.getOrDefault("login", "") : "unknown";
            String rvBody = (String) rv.getOrDefault("bodyText", "");
            Instant submittedAt = parseInstant(rv, "submittedAt");

            // 50자 미만 리뷰는 제외 ("LGTM" 등)
            String cleaned = textSanitizer.strip(rvBody);
            if (cleaned.length() < 50) continue;

            String excerpt = textSanitizer.truncateAtSentence(cleaned, 300);
            CollectedReview cr = new CollectedReview(reviewerLogin, excerpt, submittedAt);

            // 리뷰어별 최신본 유지
            latestByReviewer.merge(reviewerLogin, cr, (existing, incoming) ->
                    incoming.submittedAt() != null && existing.submittedAt() != null
                            && incoming.submittedAt().isAfter(existing.submittedAt())
                            ? incoming : existing
            );
        }
        return new ArrayList<>(latestByReviewer.values());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractLabelNodes(Map<String, Object> node) {
        Map<String, Object> labelsMap = (Map<String, Object>) node.get("labels");
        if (labelsMap == null) return List.of();
        List<Map<String, Object>> labelNodes = (List<Map<String, Object>>) labelsMap.get("nodes");
        return labelNodes != null ? labelNodes : List.of();
    }

    private List<Map<String, Object>> extractLabelNames(Map<String, Object> node) {
        return extractLabelNodes(node);
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
}
