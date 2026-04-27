package com.back.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // JVM 전체에서 한 번만 생성 — 모든 Spring 컨텍스트가 공유
    private static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withTmpFs(Map.of("/data", "rw"))
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        REDIS_CONTAINER.start();
        // Spring이 컨텍스트를 생성하기 전 JVM 시스템 프로퍼티로 포트를 미리 고정
        System.setProperty("spring.data.redis.host", REDIS_CONTAINER.getHost());
        System.setProperty("spring.data.redis.port",
            String.valueOf(REDIS_CONTAINER.getMappedPort(6379)));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withCommand("postgres -c max_connections=300")
            .withReuse(true);
    }
}
