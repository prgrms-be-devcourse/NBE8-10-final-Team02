package com.back.backend.domain.ai.client.gemini;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gemini API 연동에 필요한 빈을 등록
 * RestClient에 baseUrl과 타임아웃을 설정하고, GeminiClient를 생성
 * 나중에 OpenAI 추가 시 OpenAiClientConfig를 동일 구조로 만들면 됨
 */
@Configuration
@Profile("!load-test")
@EnableConfigurationProperties(GeminiClientProperties.class) // GeminiClientProperties 활성화
public class GeminiClientConfig {

    @Bean
    public GeminiClient geminiClient(GeminiClientProperties properties) {
        RestClient restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(clientHttpRequestFactory(properties))
            .build();

        return new GeminiClient(restClient, properties);
    }

    /**
     * 타임아웃이 적용된 RequestFactory를 생성함
     * connect/read 타임아웃은 application.yml의 ai.gemini.timeout에서 가져옴
     */
    private ClientHttpRequestFactory clientHttpRequestFactory(GeminiClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); // JDK 기본 GTTP 클라이언트
        factory.setConnectTimeout(properties.timeout().connect());
        factory.setReadTimeout(properties.timeout().read());
        return factory;
    }
}
