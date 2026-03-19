package com.back.backend.global.security.apikey;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 기반 API Key 검증 서비스
 * <p>
 * Redis에 apiKey -> userId 매핑을 저장하고 검증합니다.
 * Key 형식: "apiKey:{apiKeyValue}" -> "{userId}"
 */
@Service
public class ApiKeyService {

    private static final String API_KEY_PREFIX = "apiKey:";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofDays(30); // 30일 만료

    private final StringRedisTemplate redisTemplate;

    public ApiKeyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 새로운 API Key를 생성하고 Redis에 저장
     *
     * @param userId 사용자 ID
     * @return 생성된 API Key
     */
    public String createApiKey(long userId) {
        String apiKey = generateApiKey();
        String key = API_KEY_PREFIX + apiKey;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), DEFAULT_EXPIRATION);
        return apiKey;
    }

    /**
     * API Key를 생성하고 만료 시간을 지정
     *
     * @param userId     사용자 ID
     * @param expiration 만료 시간
     * @return 생성된 API Key
     */
    public String createApiKey(long userId, Duration expiration) {
        String apiKey = generateApiKey();
        String key = API_KEY_PREFIX + apiKey;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), expiration);
        return apiKey;
    }

    /**
     * API Key가 유효한지 검증하고 userId를 반환
     *
     * @param apiKey API Key
     * @return userId (유효하지 않으면 null)
     */
    public Long validateAndGetUserId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String key = API_KEY_PREFIX + apiKey;
        String userIdStr = redisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            return null;
        }

        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * API Key 무효화 (삭제)
     *
     * @param apiKey API Key
     */
    public void invalidateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        String key = API_KEY_PREFIX + apiKey;
        redisTemplate.delete(key);
    }

    /**
     * TODO
     * 사용자의 모든 API Key를 무효화
     * (로그아웃 또는 보안 이슈 시 사용)
     *
     * @param userId 사용자 ID
     */
    public void invalidateAllApiKeysForUser(long userId) {
        // 실제 운영에서는 userId -> Set<apiKey> 매핑도 관리해야 함
        // 간단한 구현을 위해 현재는 생략
    }

    private String generateApiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
