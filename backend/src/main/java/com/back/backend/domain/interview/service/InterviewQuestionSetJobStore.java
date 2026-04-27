package com.back.backend.domain.interview.service;

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
import java.util.UUID;

/**
 * 면접 질문 세트 AI 생성 작업 상태를 Redis에서 관리한다.
 *
 * Redis 키 구조:
 *   interview:qs-gen:{userId}:{jobId}  →  JSON  TTL 15분
 *
 * 상태 전이:
 *   PENDING → IN_PROGRESS → COMPLETED
 *                         → FAILED
 *
 * applicationId가 아닌 jobId(UUID) 기준 — 동일 application에서 여러 세트 생성이 가능하므로
 */
@Service
public class InterviewQuestionSetJobStore {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionSetJobStore.class);
    private static final String KEY_PREFIX = "interview:qs-gen:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public InterviewQuestionSetJobStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobData(
            String jobId,
            InterviewQuestionSetJobStatus status,
            Long questionSetId,
            Instant startedAt,
            Instant completedAt,
            String error
    ) {}

    /** 새 작업을 PENDING 상태로 등록하고 jobId를 반환한다. */
    public String create(long userId) {
        String jobId = UUID.randomUUID().toString();
        save(userId, jobId, new JobData(jobId, InterviewQuestionSetJobStatus.PENDING, null, Instant.now(), null, null));
        log.info("[QsGen] PENDING: userId={}, jobId={}", userId, jobId);
        return jobId;
    }

    public void setInProgress(long userId, String jobId) {
        Instant startedAt = get(userId, jobId).map(JobData::startedAt).orElseGet(Instant::now);
        save(userId, jobId, new JobData(jobId, InterviewQuestionSetJobStatus.IN_PROGRESS, null, startedAt, null, null));
        log.info("[QsGen] IN_PROGRESS: userId={}, jobId={}", userId, jobId);
    }

    public void setCompleted(long userId, String jobId, long questionSetId) {
        Instant startedAt = get(userId, jobId).map(JobData::startedAt).orElse(null);
        save(userId, jobId, new JobData(jobId, InterviewQuestionSetJobStatus.COMPLETED, questionSetId, startedAt, Instant.now(), null));
        log.info("[QsGen] COMPLETED: userId={}, jobId={}, questionSetId={}", userId, jobId, questionSetId);
    }

    public void setFailed(long userId, String jobId, String error) {
        Instant startedAt = get(userId, jobId).map(JobData::startedAt).orElse(null);
        save(userId, jobId, new JobData(jobId, InterviewQuestionSetJobStatus.FAILED, null, startedAt, Instant.now(), error));
        log.warn("[QsGen] FAILED: userId={}, jobId={}, error={}", userId, jobId, error);
    }

    public Optional<JobData> get(long userId, String jobId) {
        String key = buildKey(userId, jobId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, JobData.class));
        } catch (JsonProcessingException e) {
            log.warn("[QsGen] 역직렬화 실패: key={}, error={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void save(long userId, String jobId, JobData data) {
        String key = buildKey(userId, jobId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), TTL);
        } catch (JsonProcessingException e) {
            log.error("[QsGen] 직렬화 실패: key={}, error={}", key, e.getMessage());
        }
    }

    private String buildKey(long userId, String jobId) {
        return KEY_PREFIX + userId + ":" + jobId;
    }
}
