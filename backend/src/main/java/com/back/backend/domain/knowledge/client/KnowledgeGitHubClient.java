package com.back.backend.domain.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * knowledge sync 전용 GitHub Contents API 클라이언트.
 *
 * 기존 GithubApiClient는 유저 OAuth 토큰을 요청마다 주입하는 구조라 분리한다.
 * 이 클라이언트는 개인 PAT를 @Value로 고정 주입해 사용한다.
 * 토큰 미설정 시 비인증 요청(rate limit 60/h)으로 동작한다.
 *
 * 주의: 모든 메서드는 트랜잭션 밖에서 호출해야 한다.
 */
@Service
public class KnowledgeGitHubClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGitHubClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String token;

    public KnowledgeGitHubClient(
            @Value("${knowledge.github.token:}") String token,
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl
    ) {
        this.token = token;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "interview-platform/1.0")
                .build();
    }

    /**
     * repo의 path 내용을 가져온다.
     * path가 디렉토리이면 하위 .md 파일 전체를 수집한다.
     *
     * @return Map<filePath, rawContent> — 실패 시 빈 Map
     */
    public Map<String, String> fetchContent(String repo, String path) {
        String[] parts = repo.split("/", 2);
        String owner = parts[0];
        String repoName = parts[1];

        log.debug("Fetching {}/{} (token={})", repo, path, token.isBlank() ? "none(rate-limited)" : "set");

        try {
            String body = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repoName, path)
                    .headers(h -> {
                        if (!token.isBlank()) h.set("Authorization", "Bearer " + token);
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new RuntimeException("404 Not found: " + path); })
                    .onStatus(status -> status.value() == 403,
                            (req, res) -> { throw new RuntimeException("403 Forbidden (rate limit?): " + path); })
                    .onStatus(status -> status.value() == 429,
                            (req, res) -> { throw new RuntimeException("429 Rate limited: " + path); })
                    .onStatus(status -> !status.is2xxSuccessful(),
                            (req, res) -> { throw new RuntimeException("HTTP " + res.getStatusCode() + ": " + path); })
                    .body(String.class);

            if (body == null || body.isBlank()) {
                log.warn("Empty response for {}/{}", repo, path);
                return Map.of();
            }

            JsonNode node = MAPPER.readTree(body);

            if (node.isArray()) {
                log.debug("Directory listing {}/{}: {} entries", repo, path, node.size());
                Map<String, String> result = fetchDirectory(repo, node);
                log.debug("Directory {}/{} → {} .md files fetched", repo, path, result.size());
                return result;
            } else {
                Map<String, String> result = fetchFile(node);
                log.debug("File {}/{} → {}", repo, path, result.isEmpty() ? "skipped (encoding issue)" : "ok");
                return result;
            }

        } catch (Exception e) {
            log.warn("Failed to fetch {}/{}: {}", repo, path, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> fetchDirectory(String repo, JsonNode listing) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode item : listing) {
            if ("file".equals(item.path("type").asText())
                    && item.path("name").asText().endsWith(".md")) {
                result.putAll(fetchContent(repo, item.path("path").asText()));
            }
        }
        return result;
    }

    private Map<String, String> fetchFile(JsonNode node) {
        String encoding = node.path("encoding").asText();
        String rawContent = node.path("content").asText();
        String filePath = node.path("path").asText();

        if (!"base64".equals(encoding) || rawContent.isBlank()) return Map.of();

        String cleaned = rawContent.replaceAll("\\s", "");
        String decoded = new String(Base64.getDecoder().decode(cleaned), StandardCharsets.UTF_8);
        return Map.of(filePath, decoded);
    }
}
