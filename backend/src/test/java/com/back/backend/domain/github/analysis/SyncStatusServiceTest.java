package com.back.backend.domain.github.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncStatusServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    //Redis의 Key-Value 구조(가장 기본적인 String 타입)를 다루기 위한 인터페이스를 가짜 객체(Mock)로 만든 코드
    @Mock
    private ValueOperations<String, String> valueOps;

    private SyncStatusService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new SyncStatusService(redisTemplate, objectMapper);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ─────────────────────────────────────────────────
    // Redis 키 포맷
    // ─────────────────────────────────────────────────

    @Test
    void getStatus_usesCorrectKeyFormat() {
        given(valueOps.get("sync:status:1:2")).willReturn(null);

        service.getStatus(1L, 2L);

        verify(valueOps).get("sync:status:1:2");
    }

    // ─────────────────────────────────────────────────
    // getStatus
    // ─────────────────────────────────────────────────

    @Test
    void getStatus_noKeyInRedis_returnsEmpty() {
        given(valueOps.get("sync:status:1:2")).willReturn(null);

        Optional<SyncStatusService.SyncStatusData> result = service.getStatus(1L, 2L);

        assertThat(result).isEmpty();
    }

    @Test
    void getStatus_validJson_returnsData() throws Exception {
        SyncStatusService.SyncStatusData data = new SyncStatusService.SyncStatusData(
                2L, SyncStatus.IN_PROGRESS, "clone",
                Instant.parse("2026-01-01T00:00:00Z"), null, null, null);
        String json = objectMapper.writeValueAsString(data);
        given(valueOps.get("sync:status:1:2")).willReturn(json);

        Optional<SyncStatusService.SyncStatusData> result = service.getStatus(1L, 2L);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(SyncStatus.IN_PROGRESS);
        assertThat(result.get().step()).isEqualTo("clone");
        assertThat(result.get().repositoryId()).isEqualTo(2L);
    }

    @Test
    void getStatus_malformedJson_returnsEmpty() {
        given(valueOps.get("sync:status:1:2")).willReturn("NOT_VALID_JSON{{{");

        Optional<SyncStatusService.SyncStatusData> result = service.getStatus(1L, 2L);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────
    // getStatusBulk
    // ─────────────────────────────────────────────────

    @Test
    void getStatusBulk_emptyList_returnsEmptyMap() {
        Map<Long, SyncStatusService.SyncStatusData> result = service.getStatusBulk(1L, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void getStatusBulk_allNull_returnsEmptyMap() {
        given(valueOps.multiGet(List.of("sync:status:1:10", "sync:status:1:20")))
                .willReturn(Arrays.asList(null, null));

        Map<Long, SyncStatusService.SyncStatusData> result = service.getStatusBulk(1L, List.of(10L, 20L));

        assertThat(result).isEmpty();
    }

    @Test
    void getStatusBulk_somePresentSomeMissing_onlyIncludesPresent() throws Exception {
        SyncStatusService.SyncStatusData data = new SyncStatusService.SyncStatusData(
                20L, SyncStatus.COMPLETED, null, null, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        String json = objectMapper.writeValueAsString(data);

        given(valueOps.multiGet(List.of("sync:status:1:10", "sync:status:1:20")))
                .willReturn(Arrays.asList(null, json));

        Map<Long, SyncStatusService.SyncStatusData> result = service.getStatusBulk(1L, List.of(10L, 20L));

        assertThat(result).doesNotContainKey(10L);
        assertThat(result).containsKey(20L);
        assertThat(result.get(20L).status()).isEqualTo(SyncStatus.COMPLETED);
    }

    @Test
    void getStatusBulk_usesCorrectKeys() throws Exception {
        given(valueOps.multiGet(List.of("sync:status:5:100", "sync:status:5:200")))
                .willReturn(Arrays.asList(null, null));

        service.getStatusBulk(5L, List.of(100L, 200L));

        verify(valueOps).multiGet(List.of("sync:status:5:100", "sync:status:5:200"));
    }

    // ─────────────────────────────────────────────────
    // 상태 전이
    // ─────────────────────────────────────────────────

    @Test
    void setPending_savesWithPendingStatus() throws Exception {
        Instant estimatedEnd = Instant.parse("2026-06-01T00:05:00Z");

        service.setPending(1L, 2L, estimatedEnd);

        // 저장된 JSON에 PENDING 상태가 포함되어 있는지 ArgumentCaptor로 검증
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(
                org.mockito.ArgumentMatchers.eq("sync:status:1:2"),
                jsonCaptor.capture(),
                org.mockito.ArgumentMatchers.any());

        SyncStatusService.SyncStatusData saved =
                objectMapper.readValue(jsonCaptor.getValue(), SyncStatusService.SyncStatusData.class);
        assertThat(saved.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(saved.estimatedEndAt()).isEqualTo(estimatedEnd);
        assertThat(saved.step()).isNull();
    }

    @Test
    void setSkipped_savesWithSkippedStatusAndCompletedAt() throws Exception {
        service.setSkipped(1L, 2L);

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(
                org.mockito.ArgumentMatchers.eq("sync:status:1:2"),
                jsonCaptor.capture(),
                org.mockito.ArgumentMatchers.any());

        SyncStatusService.SyncStatusData saved =
                objectMapper.readValue(jsonCaptor.getValue(), SyncStatusService.SyncStatusData.class);
        assertThat(saved.status()).isEqualTo(SyncStatus.SKIPPED);
        assertThat(saved.completedAt()).isNotNull();
    }

    @Test
    void setFailed_savesErrorMessage() throws Exception {
        // setFailed는 내부에서 getStatus를 한 번 호출 (startedAt 유지)
        given(valueOps.get("sync:status:1:2")).willReturn(null);

        service.setFailed(1L, 2L, "git clone failed");

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(
                org.mockito.ArgumentMatchers.eq("sync:status:1:2"),
                jsonCaptor.capture(),
                org.mockito.ArgumentMatchers.any());

        SyncStatusService.SyncStatusData saved =
                objectMapper.readValue(jsonCaptor.getValue(), SyncStatusService.SyncStatusData.class);
        assertThat(saved.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(saved.error()).isEqualTo("git clone failed");
    }
}
