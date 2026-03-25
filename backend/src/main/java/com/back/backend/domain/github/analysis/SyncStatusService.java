package com.back.backend.domain.github.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 분석 파이프라인(clone → 정적 분석 → 요약 생성)의 진행 상태를 Redis에서 관리한다.
 *
 * Redis 키 구조:
 *   sync:status:{userId}:{repositoryId}  →  JSON (SyncStatusData)  TTL 24h
 *
 * 주의:
 *   - 이 서비스는 커밋 저장(GithubSyncService)과는 별개로 분석 파이프라인 상태만 관리한다.
 *   - 키에 userId가 포함되므로 다른 사용자의 상태를 조회할 수 없다.
 */
@Service
public class SyncStatusService {

    private static final Logger log = LoggerFactory.getLogger(SyncStatusService.class);
    private static final String KEY_PREFIX = "sync:status:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SyncStatusService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────
    // 도메인 데이터 타입
    // ─────────────────────────────────────────────────

    /**
     * Redis에 저장되는 분석 상태 데이터.
     * null 허용 필드는 상태에 따라 선택적으로 채워진다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SyncStatusData(
            Long repositoryId,
            SyncStatus status,
            String step,             // IN_PROGRESS 시 현재 단계 (significance_check|clone|analysis|summary)
            Instant startedAt,       // 처리 시작 시각
            Instant estimatedEndAt,  // 예상 완료 시각 (PENDING/IN_PROGRESS 시)
            Instant completedAt,     // 완료 시각 (COMPLETED/SKIPPED/FAILED 시)
            String error             // 오류 메시지 (FAILED 시)
    ) {}

    // ─────────────────────────────────────────────────
    // 상태 전이 메서드
    // ─────────────────────────────────────────────────

    /**
     * 분석 요청을 대기 상태로 등록한다.
     *
     * @param estimatedEndAt 예상 완료 시각 (null이면 추정 불가)
     */
    public void setPending(Long userId, Long repositoryId, Instant estimatedEndAt) {
        SyncStatusData data = new SyncStatusData(
                repositoryId,
                SyncStatus.PENDING,
                null,
                null,
                estimatedEndAt,
                null,
                null
        );
        save(userId, repositoryId, data);
        log.info("Sync status set to PENDING: userId={}, repoId={}", userId, repositoryId);
    }

    /**
     * 분석 파이프라인의 특정 단계 진행 중 상태로 갱신한다.
     *
     * @param step 현재 단계: significance_check | clone | analysis | summary
     */
    public void setInProgress(Long userId, Long repositoryId, String step) {
        // Redis 1회 읽기로 startedAt, estimatedEndAt 동시 추출
        SyncStatusData prev = getStatus(userId, repositoryId).orElse(null);
        Instant startedAt    = (prev != null && prev.startedAt() != null) ? prev.startedAt() : Instant.now();
        Instant estimatedEndAt = prev != null ? prev.estimatedEndAt() : null;

        SyncStatusData data = new SyncStatusData(
                repositoryId,
                SyncStatus.IN_PROGRESS,
                step,
                startedAt,
                estimatedEndAt,
                null,
                null
        );
        save(userId, repositoryId, data);
        log.info("Sync status set to IN_PROGRESS step={}: userId={}, repoId={}", step, userId, repositoryId);
    }

    /** 분석 완료 상태로 갱신한다. */
    public void setCompleted(Long userId, Long repositoryId) {
        Instant startedAt = getStatus(userId, repositoryId)
                .map(SyncStatusData::startedAt)
                .orElse(null);

        SyncStatusData data = new SyncStatusData(
                repositoryId,
                SyncStatus.COMPLETED,
                null,
                startedAt,
                null,
                Instant.now(),
                null
        );
        save(userId, repositoryId, data);
        log.info("Sync status set to COMPLETED: userId={}, repoId={}", userId, repositoryId);
    }

    /** significance check 미달로 분석을 생략했을 때 상태를 기록한다. */
    public void setSkipped(Long userId, Long repositoryId) {
        SyncStatusData data = new SyncStatusData(
                repositoryId,
                SyncStatus.SKIPPED,
                null,
                null,
                null,
                Instant.now(),
                null
        );
        save(userId, repositoryId, data);
        log.info("Sync status set to SKIPPED: userId={}, repoId={}", userId, repositoryId);
    }

    /**
     * 오류 발생으로 분석 실패 상태를 기록한다.
     *
     * @param error 사용자에게 노출 가능한 오류 요약 메시지
     */
    public void setFailed(Long userId, Long repositoryId, String error) {
        Instant startedAt = getStatus(userId, repositoryId)
                .map(SyncStatusData::startedAt)
                .orElse(null);

        SyncStatusData data = new SyncStatusData(
                repositoryId,
                SyncStatus.FAILED,
                null,
                startedAt,
                null,
                Instant.now(),
                error
        );
        save(userId, repositoryId, data);
        log.warn("Sync status set to FAILED: userId={}, repoId={}, error={}", userId, repositoryId, error);
    }

    // ─────────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────────

    /**
     * 현재 분석 상태를 조회한다.
     * TTL 만료 또는 미등록이면 빈 Optional을 반환한다.
     */
    public Optional<SyncStatusData> getStatus(Long userId, Long repositoryId) {
        String key = buildKey(userId, repositoryId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, SyncStatusData.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize sync status for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private void save(Long userId, Long repositoryId, SyncStatusData data) {
        String key = buildKey(userId, repositoryId);
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize sync status for key={}: {}", key, e.getMessage());
            // Redis 직렬화 실패는 분석 파이프라인을 막지 않는다 - 조용히 실패
        }
    }

    private String buildKey(Long userId, Long repositoryId) {
        return KEY_PREFIX + userId + ":" + repositoryId;
    }
}
