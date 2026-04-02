package com.back.backend.domain.followup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FollowupRulesProperties.class)
public class FollowupRulesConfig {
}
