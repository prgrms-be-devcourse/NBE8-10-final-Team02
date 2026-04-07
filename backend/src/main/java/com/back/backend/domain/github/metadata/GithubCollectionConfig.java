package com.back.backend.domain.github.metadata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * GitHub 메타데이터 수집 설정 활성화.
 * application.yml의 {@code github.collection.*}, {@code github.metadata.*} 블록을 바인딩한다.
 */
@Configuration
@EnableConfigurationProperties(GithubCollectionProperties.class)
public class GithubCollectionConfig {
}
