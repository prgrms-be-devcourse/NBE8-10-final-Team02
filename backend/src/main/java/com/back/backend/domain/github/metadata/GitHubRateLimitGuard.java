package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.service.GithubApiClient;
import com.back.backend.domain.github.service.GithubApiClient.RateLimitInfo;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * GitHub API 호출 전 Rate Limit 잔량을 확인하여 불필요한 429 수신을 방지한다.
 *
 * <p>Redis 캐시 전략:
 * <ul>
 *   <li>키: {@code github:ratelimit:{cacheKey}} (보통 userId 기반)</li>
 *   <li>값: "graphqlRemaining:restRemaining:resetEpoch" 단순 문자열</li>
 *   <li>TTL: {@code check-cache-ttl-seconds} (기본 60초)</li>
 * </ul>
 *
 * <p>Rate Limit 조회 자체가 REST API를 소모하므로 캐시로 불필요한 호출을 줄인다.
 */
@Service
public class GitHubRateLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(GitHubRateLimitGuard.class);
    private static final String CACHE_KEY_PREFIX = "github:ratelimit:";

    private final GithubApiClient githubApiClient;
    private final StringRedisTemplate redisTemplate;
    private final GithubCollectionProperties properties;

    public GitHubRateLimitGuard(
            GithubApiClient githubApiClient,
            StringRedisTemplate redisTemplate,
            GithubCollectionProperties properties
    ) {
        this.githubApiClient = githubApiClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * GraphQL Rate Limit 잔량이 최소 임계값 이상인지 확인한다.
     *
     * @param accessToken OAuth 액세스 토큰 (로그에 남기지 않음)
     * @param cacheKey    Redis 캐시 키 식별자 (보통 userId)
     * @throws ServiceException 잔량 부족 시 {@code GITHUB_RATE_LIMIT_EXCEEDED}
     */
    public void checkGraphQlLimit(String accessToken, String cacheKey) {
        int minimum = properties.getMetadata().getRateLimit().getGraphqlMinimumRemaining();
        RateLimitInfo info = fetchOrCached(accessToken, cacheKey);

        if (info.graphqlRemaining() < minimum) {
            long retryAfter = Math.max(0, info.resetAt().getEpochSecond() - Instant.now().getEpochSecond());
            log.warn("GitHub GraphQL rate limit low: remaining={}, resetAt={}",
                    info.graphqlRemaining(), info.resetAt());
            throw new ServiceException(
                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GitHub API 호출 한도에 도달했습니다. 잠시 후 다시 시도해주세요.",
                    true,
                    (int) retryAfter,
                    null
            );
        }
    }

    /**
     * REST Rate Limit 잔량이 최소 임계값 이상인지 확인한다.
     */
    public void checkRestLimit(String accessToken, String cacheKey) {
        int minimum = properties.getMetadata().getRateLimit().getRestMinimumRemaining();
        RateLimitInfo info = fetchOrCached(accessToken, cacheKey);

        if (info.restRemaining() < minimum) {
            long retryAfter = Math.max(0, info.resetAt().getEpochSecond() - Instant.now().getEpochSecond());
            log.warn("GitHub REST rate limit low: remaining={}, resetAt={}",
                    info.restRemaining(), info.resetAt());
            throw new ServiceException(
                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GitHub API 호출 한도에 도달했습니다. 잠시 후 다시 시도해주세요.",
                    true,
                    (int) retryAfter,
                    null
            );
        }
    }

    /** 캐시 히트 시 반환, 미스 시 API 호출 후 캐시 저장 */
    private RateLimitInfo fetchOrCached(String accessToken, String cacheKey) {
        String redisKey = CACHE_KEY_PREFIX + cacheKey;
        String cached = redisTemplate.opsForValue().get(redisKey);

        if (cached != null) {
            return parse(cached);
        }

        RateLimitInfo info = githubApiClient.getRateLimit(accessToken);
        int ttl = properties.getMetadata().getRateLimit().getCheckCacheTtlSeconds();
        String value = info.graphqlRemaining() + ":" + info.restRemaining() + ":" + info.resetAt().getEpochSecond();
        redisTemplate.opsForValue().set(redisKey, value, Duration.ofSeconds(ttl));
        return info;
    }

    private RateLimitInfo parse(String cached) {
        String[] parts = cached.split(":");
        if (parts.length != 3) return new RateLimitInfo(0, 0, Instant.now().plusSeconds(3600));
        return new RateLimitInfo(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Instant.ofEpochSecond(Long.parseLong(parts[2]))
        );
    }
}
