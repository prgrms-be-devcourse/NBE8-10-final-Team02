package com.back.backend.domain.github.portfolio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 배치 토큰 예산 설정 활성화.
 *
 * <p>{@link BatchTokenBudgetProperties}를 {@code @ConfigurationProperties} 빈으로 등록한다.
 * application.yml의 {@code ai.portfolio.batch.global-budget-chars} 값이 주입된다.
 */
@Configuration
@EnableConfigurationProperties(BatchTokenBudgetProperties.class)
public class BatchTokenBudgetConfig {
}
