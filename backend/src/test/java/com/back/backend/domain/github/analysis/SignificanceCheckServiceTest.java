package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignificanceCheckServiceTest {

    @Mock
    private GithubCommitRepository commitRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SignificanceCheckService service;
    private GithubRepository repo;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new SignificanceCheckService(commitRepository, redisTemplate, objectMapper);

        repo = mock(GithubRepository.class);
        given(repo.getId()).willReturn(1L);

        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ─────────────────────────────────────────────────
    // 최초 분석 (lastAnalyzedAt 없음)
    // ─────────────────────────────────────────────────

    @Test
    void firstTime_noCommits_returnsFalse() {
        given(valueOps.get(anyString())).willReturn(null);
        given(commitRepository.findByRepositoryAndUserCommitTrue(repo)).willReturn(List.of());

        assertThat(service.isSignificant(repo, 10L)).isFalse();
    }

    @Test
    void firstTime_hasCommits_returnsTrue() {
        given(valueOps.get(anyString())).willReturn(null);
        given(commitRepository.findByRepositoryAndUserCommitTrue(repo))
                .willReturn(List.of(commit("init: project setup")));

        assertThat(service.isSignificant(repo, 10L)).isTrue();
    }

    // ─────────────────────────────────────────────────
    // 재분석 (lastAnalyzedAt 있음)
    // ─────────────────────────────────────────────────

    @Test
    void reanalysis_noNewCommits_returnsFalse() throws Exception {
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of());

        assertThat(service.isSignificant(repo, 10L)).isFalse();
    }

    @Test
    void reanalysis_fourPlainCommits_scoreTwelve_returnsFalse() throws Exception {
        // 4 commits × 3pts = 12 < threshold(15) → false
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of(
                        commit("chore: cleanup"),
                        commit("chore: cleanup"),
                        commit("chore: cleanup"),
                        commit("chore: cleanup")
                ));

        assertThat(service.isSignificant(repo, 10L)).isFalse();
    }

    @Test
    void reanalysis_fivePlainCommits_scoreFifteen_returnsTrue() throws Exception {
        // 5 commits × 3pts = 15 = threshold(15) → true
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of(
                        commit("chore: cleanup"),
                        commit("chore: cleanup"),
                        commit("chore: cleanup"),
                        commit("chore: cleanup"),
                        commit("chore: cleanup")
                ));

        assertThat(service.isSignificant(repo, 10L)).isTrue();
    }

    @Test
    void reanalysis_twoFeatureCommits_scoreEighteen_returnsTrue() throws Exception {
        // 2 commits × (3 + 5) pts = 16 >= 15 → true
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of(
                        commit("feat: add login"),
                        commit("feat: add logout")
                ));

        assertThat(service.isSignificant(repo, 10L)).isTrue();
    }

    // ─────────────────────────────────────────────────
    // 키워드 인식 (간접 검증: feature keyword → +5pts)
    // ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "feat: add login",
            "feature: new dashboard",
            "add user profile",
            "implement oauth",
            "refactor service layer",
            "improve performance",
            "enhance caching",
            "update dependencies",
            "migrate to v2",
            "create new endpoint",
            "introduce rate limiting"
    })
    void featureKeywordCommit_oneCommitExceedsThreshold(String message) throws Exception {
        // feature keyword commit: 3 + 5 = 8pts → 2개면 16 >= 15 → true
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of(
                        commit(message),
                        commit(message)
                ));

        assertThat(service.isSignificant(repo, 10L)).isTrue();
    }

    @Test
    void nonFeatureKeyword_doesNotAddBonusPoints() throws Exception {
        // "fix:", "docs:" 등은 키워드 아님 → 3pts/commit only
        // 4 × 3 = 12 < 15 → false
        stubLastAnalyzedAt(10L, 1L, Instant.now().minusSeconds(3600));
        given(commitRepository.findByRepositoryAndUserCommitTrueAndCommittedAtAfter(eq(repo), any()))
                .willReturn(List.of(
                        commit("fix: null pointer"),
                        commit("docs: correct typos"),
                        commit("test: missing coverage"),
                        commit("style: format code")
                ));

        assertThat(service.isSignificant(repo, 10L)).isFalse();
    }

    // ─────────────────────────────────────────────────
    // Redis 키 포맷
    // ─────────────────────────────────────────────────

    @Test
    void redisKey_hasCorrectFormat() throws Exception {
        GithubRepository repoWith7 = mock(GithubRepository.class);
        given(repoWith7.getId()).willReturn(7L);

        given(valueOps.get("significance:42:7")).willReturn(null);
        given(commitRepository.findByRepositoryAndUserCommitTrue(repoWith7)).willReturn(List.of());

        service.isSignificant(repoWith7, 42L);

        org.mockito.Mockito.verify(valueOps).get("significance:42:7");
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    private GithubCommit commit(String message) {
        return GithubCommit.builder()
                .repository(repo)
                .githubCommitSha("sha-" + message.hashCode())
                .commitMessage(message)
                .userCommit(true)
                .committedAt(Instant.now())
                .build();
    }

    private void stubLastAnalyzedAt(Long userId, Long repoId, Instant lastAnalyzedAt) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(
                new SignificanceCheckService.SignificanceRecord(lastAnalyzedAt));
        given(valueOps.get("significance:" + userId + ":" + repoId)).willReturn(json);
    }
}
