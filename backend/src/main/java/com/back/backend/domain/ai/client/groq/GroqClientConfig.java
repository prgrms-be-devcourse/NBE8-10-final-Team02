package com.back.backend.domain.ai.client.groq;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Groq API 연동에 필요한 빈을 등록
 * RestClient에 baseUrl과 타임아웃을 설정하고, GroqClient를 생성
 * Gemini 할당량 초과 시 fallback으로 자동 전환됨
 */
@Configuration
@Profile("!load-test")
@EnableConfigurationProperties(GroqClientProperties.class)
public class GroqClientConfig {

    @Bean
    public GroqClient groqClient(GroqClientProperties properties) {
        RestClient restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(clientHttpRequestFactory(properties))
            .build();

        return new GroqClient(restClient, properties);
    }

    /**
     * 타임아웃이 적용된 RequestFactory를 생성
     * connect/read 타임아웃은 application.yml의 ai.groq.timeout에서 가져옴
     */
    private ClientHttpRequestFactory clientHttpRequestFactory(GroqClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout().connect());
        factory.setReadTimeout(properties.timeout().read());
        return factory;
    }
}
