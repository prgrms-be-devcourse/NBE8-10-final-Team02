package com.back.backend.domain.portfolio.service;

import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse.AlertItem;
import com.back.backend.domain.portfolio.service.FailedJobRedisStore.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedJobRedisStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    private FailedJobRedisStore store;

    @BeforeEach
    void setUp() {
        // ObjectMapper에 JavaTimeModule 등록 (Instant 직렬화)
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new FailedJobRedisStore(redisTemplate, objectMapper);
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    // ─────────────────────────────────────────────────
    // push() 테스트
    // ─────────────────────────────────────────────────

    @Test
    void push_정상_LPUSH_LTRIM_EXPIRE_순서로_호출된다() {
        // when
        store.push(1L, JobType.GITHUB_SYNC, "GITHUB_COMMIT_SYNC_FAILED", "커밋 동기화 실패");

        // then: LPUSH → LTRIM → EXPIRE 순서 검증
        verify(listOps).leftPush(eq("failed_jobs:1"), anyString());
        verify(listOps).trim(eq("failed_jobs:1"), eq(0L), eq(19L));
        verify(redisTemplate).expire(eq("failed_jobs:1"), eq(Duration.ofDays(7)));
    }

    @Test
    void push_Redis_예외_발생해도_예외_전파_안됨() {
        // given
        given(listOps.leftPush(anyString(), anyString())).willThrow(new RuntimeException("Redis unavailable"));

        // when / then: 예외 없이 조용히 실패해야 한다
        store.push(1L, JobType.GITHUB_ANALYSIS, "ANALYSIS_FAILED", "분석 실패");
    }

    // ─────────────────────────────────────────────────
    // getRecent() 테스트
    // ─────────────────────────────────────────────────

    @Test
    void getRecent_항목이_없으면_빈_리스트_반환() {
        // given
        given(listOps.range(anyString(), anyLong(), anyLong())).willReturn(List.of());

        // when
        List<AlertItem> result = store.getRecent(1L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getRecent_항목이_있으면_AlertItem으로_변환하여_반환() throws Exception {
        // given: 실제 JSON 직렬화로 테스트 데이터 생성
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FailedJobRedisStore.FailedJobEntry entry = new FailedJobRedisStore.FailedJobEntry(
                JobType.INTERVIEW_RESULT,
                "INTERVIEW_RESULT_GENERATION_FAILED",
                "면접 결과 생성 실패",
                java.time.Instant.parse("2026-01-01T00:00:00Z")
        );
        String json = mapper.writeValueAsString(entry);
        given(listOps.range(eq("failed_jobs:2"), eq(0L), eq(19L))).willReturn(List.of(json));

        // when
        List<AlertItem> result = store.getRecent(2L);

        // then
        assertThat(result).hasSize(1);
        AlertItem item = result.get(0);
        assertThat(item.code()).isEqualTo("INTERVIEW_RESULT_GENERATION_FAILED");
        assertThat(item.message()).contains("INTERVIEW_RESULT").contains("면접 결과 생성 실패");
        assertThat(item.occurredAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void getRecent_Redis_예외_발생해도_빈_리스트_반환() {
        // given
        given(listOps.range(anyString(), anyLong(), anyLong())).willThrow(new RuntimeException("Redis unavailable"));

        // when
        List<AlertItem> result = store.getRecent(1L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getRecent_잘못된_JSON은_건너뛰고_나머지_반환() throws Exception {
        // given: 유효한 항목 하나 + 잘못된 JSON 하나
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FailedJobRedisStore.FailedJobEntry entry = new FailedJobRedisStore.FailedJobEntry(
                JobType.GITHUB_SYNC,
                "GITHUB_COMMIT_SYNC_FAILED",
                "동기화 실패",
                java.time.Instant.now()
        );
        String validJson = mapper.writeValueAsString(entry);
        given(listOps.range(eq("failed_jobs:3"), eq(0L), eq(19L)))
                .willReturn(List.of(validJson, "INVALID_JSON{{{"));

        // when
        List<AlertItem> result = store.getRecent(3L);

        // then: 유효한 항목 1건만 반환
        assertThat(result).hasSize(1);
    }
}
