package com.back.backend.domain.portfolio.service;

import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse.AlertItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 사용자별 최근 작업 실패 이력을 Redis List로 관리한다.
 *
 * Redis 키 구조:
 *   failed_jobs:{userId}  →  JSON 배열 (FailedJobEntry, 최신순)  TTL 7일
 *
 * 설계 원칙:
 *   - LPUSH + LTRIM으로 최대 20건만 유지 (메모리 바운드)
 *   - Redis 쓰기/읽기 실패는 주 흐름을 막지 않는다 — 로그만 남기고 조용히 실패
 *   - 각 항목은 독립 JSON 문자열로 저장 (LRANGE → 역직렬화)
 */
@Service
public class FailedJobRedisStore {

    private static final Logger log = LoggerFactory.getLogger(FailedJobRedisStore.class);
    private static final String KEY_PREFIX = "failed_jobs:";
    /** List 최대 길이: 가장 오래된 항목은 자동으로 제거된다 */
    private static final long MAX_ENTRIES = 20;
    private static final Duration TTL = Duration.ofDays(7);

    // ─────────────────────────────────────────────────
    // 도메인 타입
    // ─────────────────────────────────────────────────

    /** 실패한 작업 종류 */
    public enum JobType {
        GITHUB_SYNC,
        GITHUB_ANALYSIS,
        INTERVIEW_RESULT
    }

    /**
     * Redis에 저장되는 실패 항목.
     * 역직렬화 시 미지 필드(스키마 변경)는 무시한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedJobEntry(
            JobType jobType,
            String errorCode,
            String message,
            Instant occurredAt
    ) {}

    // ─────────────────────────────────────────────────
    // 생성자 주입
    // ─────────────────────────────────────────────────

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FailedJobRedisStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────────

    /**
     * 실패 항목을 List 앞에 추가하고 TTL을 갱신한다.
     *
     * @param userId    실패를 겪은 사용자 ID
     * @param jobType   실패한 작업 종류
     * @param errorCode 서비스 ErrorCode 이름 (예: GITHUB_COMMIT_SYNC_FAILED)
     * @param message   사용자에게 보여줄 요약 메시지
     */
    public void push(Long userId, JobType jobType, String errorCode, String message) {
        String key = buildKey(userId);
        try {
            FailedJobEntry entry = new FailedJobEntry(jobType, errorCode, message, Instant.now());
            String json = objectMapper.writeValueAsString(entry);
            // LPUSH → 최신 항목이 인덱스 0
            redisTemplate.opsForList().leftPush(key, json);
            // MAX_ENTRIES 초과분 제거 (인덱스 0 ~ MAX_ENTRIES-1 유지)
            redisTemplate.opsForList().trim(key, 0, MAX_ENTRIES - 1);
            // 마지막 활동 시점 기준으로 TTL 갱신
            redisTemplate.expire(key, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize failed job entry for userId={}: {}", userId, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to push failed job to Redis for userId={}: {}", userId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    // 읽기
    // ─────────────────────────────────────────────────

    /**
     * 최근 실패 항목을 최신순으로 반환한다.
     * Redis 오류나 역직렬화 실패 시 빈 리스트를 반환한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 최신순 AlertItem 목록 (최대 MAX_ENTRIES건)
     */
    public List<AlertItem> getRecent(Long userId) {
        String key = buildKey(userId);
        try {
            List<String> jsons = redisTemplate.opsForList().range(key, 0, MAX_ENTRIES - 1);
            if (jsons == null || jsons.isEmpty()) return Collections.emptyList();

            return jsons.stream()
                    .map(json -> {
                        try {
                            FailedJobEntry entry = objectMapper.readValue(json, FailedJobEntry.class);
                            // errorCode를 AlertItem.code로, jobType + message를 AlertItem.message로 매핑
                            String alertMessage = "[" + entry.jobType().name() + "] " + entry.message();
                            return new AlertItem(entry.errorCode(), alertMessage, entry.occurredAt());
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to deserialize failed job entry: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to read failed jobs from Redis for userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
