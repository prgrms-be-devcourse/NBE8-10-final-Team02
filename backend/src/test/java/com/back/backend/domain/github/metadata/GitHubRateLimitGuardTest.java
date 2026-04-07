package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.service.GithubApiClient;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GitHubRateLimitGuard 단위 테스트.
 *
 * WireMock으로 GET /rate_limit 응답을 stub하고,
 * Redis StringRedisTemplate는 Mockito로 mock하여 캐시 미스 상황을 시뮬레이션한다.
 */
class GitHubRateLimitGuardTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GitHubRateLimitGuard guard;
    private GithubCollectionProperties properties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    private static final String ACCESS_TOKEN = "gho_test_token";
    private static final String CACHE_KEY = "user-42";

    @BeforeEach
    void setUp() {
        GithubApiClient apiClient = new GithubApiClient(
                wireMock.baseUrl(), "client-id", "client-secret");

        properties = new GithubCollectionProperties();
        // graphqlMinimumRemaining 기본값=500

        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 기본: 캐시 미스 (null 반환), set() 호출은 무시
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), anyString(), any());

        guard = new GitHubRateLimitGuard(apiClient, redisTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GraphQL 한도 확인
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkGraphQlLimit()")
    class CheckGraphQlLimit {

        @Test
        @DisplayName("GraphQL 잔량이 최소값 이상이면 예외 없음")
        void checkGraphQlLimit_doesNotThrow_whenRemainingAboveMinimum() {
            stubRateLimitResponse(5000, 5000);

            assertThatCode(() -> guard.checkGraphQlLimit(ACCESS_TOKEN, CACHE_KEY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GraphQL 잔량이 최소값(500) 미만이면 ServiceException 발생")
        void checkGraphQlLimit_throwsServiceException_whenRemainingBelowMinimum() {
            stubRateLimitResponse(499, 5000);  // graphql=499 < 500

            assertThatThrownBy(() -> guard.checkGraphQlLimit(ACCESS_TOKEN, CACHE_KEY))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(e -> {
                        ServiceException se = (ServiceException) e;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED);
                    });
        }

        @Test
        @DisplayName("GraphQL 잔량이 정확히 최소값(500)이면 예외 없음")
        void checkGraphQlLimit_doesNotThrow_whenRemainingEqualsMinimum() {
            stubRateLimitResponse(500, 5000);

            assertThatCode(() -> guard.checkGraphQlLimit(ACCESS_TOKEN, CACHE_KEY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Redis 캐시 히트 시 API를 호출하지 않고 캐시 값으로 판단")
        void checkGraphQlLimit_usesCachedValue_whenCacheHit() {
            // 캐시: graphql=5000, rest=5000, reset=now+1h
            long resetEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
            when(valueOps.get(anyString())).thenReturn("5000:5000:" + resetEpoch);

            // WireMock에 아무 stub도 없어도 예외가 발생하지 않아야 함 (API 미호출)
            assertThatCode(() -> guard.checkGraphQlLimit(ACCESS_TOKEN, CACHE_KEY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Redis 캐시에 저장된 잔량이 부족하면 ServiceException 발생")
        void checkGraphQlLimit_throwsServiceException_whenCacheShowsLowRemaining() {
            long resetEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
            when(valueOps.get(anyString())).thenReturn("100:5000:" + resetEpoch);  // graphql=100 < 500

            assertThatThrownBy(() -> guard.checkGraphQlLimit(ACCESS_TOKEN, CACHE_KEY))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(e -> {
                        ServiceException se = (ServiceException) e;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REST 한도 확인
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkRestLimit()")
    class CheckRestLimit {

        @Test
        @DisplayName("REST 잔량이 최소값 이상이면 예외 없음")
        void checkRestLimit_doesNotThrow_whenRemainingAboveMinimum() {
            stubRateLimitResponse(5000, 5000);

            assertThatCode(() -> guard.checkRestLimit(ACCESS_TOKEN, CACHE_KEY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("REST 잔량이 최소값(100) 미만이면 ServiceException 발생")
        void checkRestLimit_throwsServiceException_whenRemainingBelowMinimum() {
            stubRateLimitResponse(5000, 99);  // rest=99 < 100

            assertThatThrownBy(() -> guard.checkRestLimit(ACCESS_TOKEN, CACHE_KEY))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(e -> {
                        ServiceException se = (ServiceException) e;
                        assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private void stubRateLimitResponse(int graphqlRemaining, int coreRemaining) {
        long resetEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
        String body = """
                {
                  "resources": {
                    "graphql": { "remaining": %d, "reset": %d },
                    "core":    { "remaining": %d, "reset": %d }
                  }
                }
                """.formatted(graphqlRemaining, resetEpoch, coreRemaining, resetEpoch);

        wireMock.stubFor(get(urlEqualTo("/rate_limit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
