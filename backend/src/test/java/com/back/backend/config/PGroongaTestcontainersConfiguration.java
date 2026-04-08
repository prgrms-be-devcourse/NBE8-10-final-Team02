package com.back.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * PGroonga 전문 검색 통합 테스트용 Testcontainers 설정.
 *
 * <p>일반 {@link TestcontainersConfiguration}(postgres:16-alpine) 대신
 * PGroonga가 사전 설치된 PostgreSQL 이미지를 사용한다.
 * PGroonga 없이는 {@code &@~} 연산자와 FTS 인덱스가 동작하지 않으므로
 * 검색 관련 통합 테스트 전용으로 분리한다.</p>
 *
 * <h3>사용법</h3>
 * <p>{@link com.back.backend.support.PGroongaIntegrationTest} 메타 어노테이션을 클래스에 붙이면
 * 이 설정이 자동으로 적용된다.</p>
 *
 * <h3>이미지 핀 정책</h3>
 * <p>{@code latest} 태그 대신 SHA256 digest로 핀하여 CI에서 예기치 않은 이미지 변경을 방지한다.
 * 업그레이드 시 digest를 명시적으로 갱신한다.</p>
 *
 * <p>Redis 빈은 {@link TestcontainersConfiguration}과 공유하지 않고 독립 선언한다.
 * Spring 컨텍스트가 분리되어 있으므로 두 설정을 동시에 import하면 빈 충돌이 발생한다.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PGroongaTestcontainersConfiguration {

    /**
     * PGroonga 확장이 설치된 PostgreSQL 16 컨테이너.
     *
     * <p>digest 핀: {@code groonga/pgroonga:latest-alpine-16@sha256:630426f963bb120aa15dac3cfffcfeb249c2b9b5db534be611b4aebfe3983d85}
     * (2026-04-08 기준. 업그레이드 시 {@code docker pull groonga/pgroonga:latest-alpine-16}로 새 digest 확인 후 갱신)</p>
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
            DockerImageName.parse(
                "groonga/pgroonga@sha256:630426f963bb120aa15dac3cfffcfeb249c2b9b5db534be611b4aebfe3983d85")
                .asCompatibleSubstituteFor("postgres"))
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withCommand("postgres -c max_connections=300")
            .withReuse(true);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withTmpFs(Map.of("/data", "rw"))
            .withExposedPorts(6379)
            .withReuse(true);
    }
}
