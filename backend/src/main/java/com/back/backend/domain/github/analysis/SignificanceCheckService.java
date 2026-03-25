package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 분석 파이프라인 실행 여부를 결정하는 significance check.
 *
 * 점수 계산:
 *   - 사용자 본인 커밋 1개당 +3점
 *   - 커밋 메시지에 기능 관련 키워드 포함 시 +5점
 *   - 합산 점수 ≥ 15 → 분석 실행, 미달 → SKIPPED
 *
 * Redis 키 구조:
 *   significance:{userId}:{repositoryId}  →  JSON (SignificanceRecord)  TTL 30일
 *
 * 주의:
 *   - GitHub API를 호출하지 않는다. DB(github_commits)에 저장된 데이터만 사용한다.
 *   - 커밋이 DB에 없으면 GithubSyncService로 먼저 커밋을 저장해야 한다.
 */
@Service
public class SignificanceCheckService {

    private static final Logger log = LoggerFactory.getLogger(SignificanceCheckService.class);
    private static final String KEY_PREFIX = "significance:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final int SCORE_THRESHOLD = 15;
    private static final int SCORE_PER_COMMIT = 3;
    private static final int SCORE_PER_KEYWORD = 5;

    // 기능 관련 키워드 (소문자 비교)
    private static final Set<String> FEATURE_KEYWORDS = Set.of(
            "feat", "feature", "add", "implement", "create", "introduce",
            "refactor", "improve", "enhance", "update", "migrate"
    );

    private final GithubCommitRepository commitRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SignificanceCheckService(
            GithubCommitRepository commitRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.commitRepository = commitRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────
    // Redis 저장 데이터
    // ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignificanceRecord(
            Instant lastAnalyzedAt  // 마지막으로 분석이 실행된 시각
    ) {}

    // ─────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────

    /**
     * 분석이 필요할 만큼 변경이 충분한지 판단한다.
     *
     * 최초 분석(lastAnalyzedAt 없음): 사용자 커밋이 1개 이상이면 true.
     * 재분석: lastAnalyzedAt 이후 새 커밋의 점수 합산이 SCORE_THRESHOLD 이상이면 true.
     *
     * @return true이면 분석 실행, false이면 SKIPPED
     */
    @Transactional(readOnly = true)
    public boolean isSignificant(GithubRepository repo, Long userId) {
        Instant lastAnalyzedAt = getLastAnalyzedAt(userId, repo.getId());

        List<GithubCommit> commits;
        if (lastAnalyzedAt == null) {
            // 최초 분석: 커밋이 1개 이상이면 분석 실행
            commits = commitRepository.findByRepositoryAndUserCommitTrue(repo);
            boolean significant = !commits.isEmpty();
            log.info("Significance check (first-time): repoId={}, userCommitCount={}, result={}",
                    repo.getId(), commits.size(), significant);
            return significant;
        }

        commits = commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(
                repo, lastAnalyzedAt);

        if (commits.isEmpty()) {
            log.info("Significance check: repoId={}, no new commits since {}", repo.getId(), lastAnalyzedAt);
            return false;
        }

        int score = calculateScore(commits);
        boolean significant = score >= SCORE_THRESHOLD;
        log.info("Significance check: repoId={}, newCommits={}, score={}, threshold={}, result={}",
                repo.getId(), commits.size(), score, SCORE_THRESHOLD, significant);
        return significant;
    }

    /**
     * 분석 완료 후 lastAnalyzedAt을 현재 시각으로 갱신한다.
     * COMPLETED 또는 SKIPPED 상태에서 호출해야 한다.
     */
    public void markAnalyzed(Long userId, Long repositoryId) {
        SignificanceRecord record = new SignificanceRecord(Instant.now());
        String key = buildKey(userId, repositoryId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(record), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to save significance record for key={}: {}", key, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private Instant getLastAnalyzedAt(Long userId, Long repositoryId) {
        String key = buildKey(userId, repositoryId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            SignificanceRecord record = objectMapper.readValue(json, SignificanceRecord.class);
            return record.lastAnalyzedAt();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize significance record for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private int calculateScore(List<GithubCommit> commits) {
        int score = 0;
        for (GithubCommit commit : commits) {
            score += SCORE_PER_COMMIT;
            if (containsFeatureKeyword(commit.getCommitMessage())) {
                score += SCORE_PER_KEYWORD;
            }
        }
        return score;
    }

    private boolean containsFeatureKeyword(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();
        return FEATURE_KEYWORDS.stream().anyMatch(keyword ->
                lower.contains(keyword + ":") || lower.contains(keyword + "(")
                        || lower.startsWith(keyword + " ") || lower.contains(" " + keyword + " ")
        );
    }

    private String buildKey(Long userId, Long repositoryId) {
        return KEY_PREFIX + userId + ":" + repositoryId;
    }
}
