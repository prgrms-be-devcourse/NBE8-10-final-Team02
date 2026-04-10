package com.back.backend.domain.application.service;

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
 * 자소서 AI 답변 생성 작업 상태를 Redis에서 관리한다.
 *
 * Redis 키 구조:
 *   app:ai-gen:{userId}:{applicationId}  →  JSON  TTL 10분
 *
 * 상태 전이:
 *   PENDING → IN_PROGRESS → COMPLETED
 *                         → FAILED
 */
@Service
public class ApplicationAiGenerationJobStore {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAiGenerationJobStore.class);
    private static final String KEY_PREFIX = "app:ai-gen:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ApplicationAiGenerationJobStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobData(
            ApplicationAiGenerationJobStatus status,
            Instant startedAt,
            Instant completedAt,
            String error
    ) {}

    public void setPending(long userId, long applicationId) {
        save(userId, applicationId, new JobData(
                ApplicationAiGenerationJobStatus.PENDING, Instant.now(), null, null));
        log.info("[AppAI] PENDING: userId={}, applicationId={}", userId, applicationId);
    }

    public void setInProgress(long userId, long applicationId) {
        Instant startedAt = get(userId, applicationId)
                .map(JobData::startedAt)
                .orElseGet(Instant::now);
        save(userId, applicationId, new JobData(
                ApplicationAiGenerationJobStatus.IN_PROGRESS, startedAt, null, null));
        log.info("[AppAI] IN_PROGRESS: userId={}, applicationId={}", userId, applicationId);
    }

    public void setCompleted(long userId, long applicationId) {
        Instant startedAt = get(userId, applicationId).map(JobData::startedAt).orElse(null);
        save(userId, applicationId, new JobData(
                ApplicationAiGenerationJobStatus.COMPLETED, startedAt, Instant.now(), null));
        log.info("[AppAI] COMPLETED: userId={}, applicationId={}", userId, applicationId);
    }

    public void setFailed(long userId, long applicationId, String error) {
        Instant startedAt = get(userId, applicationId).map(JobData::startedAt).orElse(null);
        save(userId, applicationId, new JobData(
                ApplicationAiGenerationJobStatus.FAILED, startedAt, Instant.now(), error));
        log.warn("[AppAI] FAILED: userId={}, applicationId={}, error={}", userId, applicationId, error);
    }

    public Optional<JobData> get(long userId, long applicationId) {
        String key = buildKey(userId, applicationId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, JobData.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize ai-gen job for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void save(long userId, long applicationId, JobData data) {
        String key = buildKey(userId, applicationId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ai-gen job for key={}: {}", key, e.getMessage());
        }
    }

    private String buildKey(long userId, long applicationId) {
        return KEY_PREFIX + userId + ":" + applicationId;
    }
}
