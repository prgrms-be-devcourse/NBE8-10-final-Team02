package com.back.backend.domain.ai.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TruncatedJsonArrayRecoveryTest {

    private TruncatedJsonArrayRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new TruncatedJsonArrayRecovery(new ObjectMapper());
    }

    @Test
    @DisplayName("완전한 JSON 배열은 그대로 반환한다")
    void recover_completeArray_returnsSameElements() {
        String json = """
                [
                  {"repoId": "my-backend",  "project": {"key": "v1"}},
                  {"repoId": "my-frontend", "project": {"key": "v2"}}
                ]
                """;

        Optional<JsonNode> result = recovery.tryRecover(json);

        assertThat(result).isPresent();
        assertThat(result.get().size()).isEqualTo(2);
        assertThat(result.get().get(0).get("repoId").asText()).isEqualTo("my-backend");
    }

    @Test
    @DisplayName("두 번째 원소가 절단된 경우 첫 번째 원소만 복구한다")
    void recover_secondElementTruncated_returnsFirstOnly() {
        // 두 번째 repo의 project가 중간에 잘림
        String truncated = """
                [
                  {"repoId": "my-backend", "project": {"key": "v1"}},
                  {"repoId": "my-frontend", "project": {"key": "val
                """;

        Optional<JsonNode> result = recovery.tryRecover(truncated);

        assertThat(result).isPresent();
        assertThat(result.get().size()).isEqualTo(1);
        assertThat(result.get().get(0).get("repoId").asText()).isEqualTo("my-backend");
    }

    @Test
    @DisplayName("세 repo 중 두 번째까지 완성되고 세 번째가 절단된 경우")
    void recover_thirdElementTruncated_returnsTwoElements() {
        String truncated = """
                [
                  {"repoId": "repo-a", "project": {"k": "1"}},
                  {"repoId": "repo-b", "project": {"k": "2"}},
                  {"repoId": "repo-c", "project": {"k": "trun
                """;

        Optional<JsonNode> result = recovery.tryRecover(truncated);

        assertThat(result).isPresent();
        assertThat(result.get().size()).isEqualTo(2);
        assertThat(result.get().get(1).get("repoId").asText()).isEqualTo("repo-b");
    }

    @Test
    @DisplayName("첫 번째 원소조차 완성되지 않으면 empty를 반환한다")
    void recover_noCompleteElement_returnsEmpty() {
        String truncated = """
                [
                  {"repoId": "repo-a", "project": {"k": "trun
                """;

        Optional<JsonNode> result = recovery.tryRecover(truncated);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 또는 빈 문자열은 empty를 반환한다")
    void recover_nullOrBlank_returnsEmpty() {
        assertThat(recovery.tryRecover(null)).isEmpty();
        assertThat(recovery.tryRecover("")).isEmpty();
        assertThat(recovery.tryRecover("   ")).isEmpty();
    }

    @Test
    @DisplayName("배열이 아닌 단일 객체는 empty를 반환한다")
    void recover_notAnArray_returnsEmpty() {
        String obj = "{\"repoId\": \"x\", \"project\": {\"k\": \"v\"}}";

        Optional<JsonNode> result = recovery.tryRecover(obj);

        assertThat(result).isEmpty();
    }
}
