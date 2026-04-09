package com.back.backend.domain.ai.client.gemini;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
     * HTTP/2 + 커넥션 풀이 적용된 RequestFactory를 생성
     * JdkClientHttpRequestFactory는 java.net.http.HttpClient 기반으로
     * HTTP/2 멀티플렉싱, TLS 세션 재사용, 커넥션 풀을 자동 지원하여
     * 동시 요청 시 SSL 핸드쉐이크 CPU 비용을 대폭 절감함
     */
    private ClientHttpRequestFactory clientHttpRequestFactory(GeminiClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(properties.timeout().connect())
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout().read());
        return factory;
    }
}
