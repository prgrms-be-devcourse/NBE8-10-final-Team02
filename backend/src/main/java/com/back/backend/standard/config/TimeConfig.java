package com.back.backend.standard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        // 프로덕션 환경에서는 진짜 시스템의 현재 시간을 제공하는 시계를 등록합니다!
        return Clock.systemUTC(); // 또는 Clock.systemDefaultZone()
    }
}
