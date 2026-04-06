package com.back.backend.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬 개발 환경 전용 Flyway 전략.
 *
 * <p>로컬 DB에 적용된 마이그레이션의 체크섬이 현재 파일과 불일치할 때
 * (예: 줄 끝 문자 변환으로 인한 CRLF/LF 차이)
 * {@code repair()} → {@code migrate()} 순서로 실행해 자동으로 복구합니다.
 */
@Configuration
@Profile("dev")
public class FlywayDevConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
