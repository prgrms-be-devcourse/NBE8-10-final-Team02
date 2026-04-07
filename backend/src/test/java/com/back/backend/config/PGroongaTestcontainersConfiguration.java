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
 * <pre>
 * {@literal @}SpringBootTest
 * {@literal @}ActiveProfiles("test")
 * {@literal @}Import(PGroongaTestcontainersConfiguration.class)
 * class DocumentSearchRepositoryTest { ... }
 * </pre>
 *
 * <p>Redis 컨테이너는 {@link TestcontainersConfiguration}과 동일하게 구성한다.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PGroongaTestcontainersConfiguration {

    /**
     * PGroonga 확장이 설치된 PostgreSQL 16 컨테이너.
     *
     * <p>{@code asCompatibleSubstituteFor("postgres")}로 Testcontainers의
     * PostgreSQL 드라이버 자동 감지가 동작하도록 선언한다.</p>
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
            DockerImageName.parse("groonga/pgroonga:latest-alpine-16")
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
