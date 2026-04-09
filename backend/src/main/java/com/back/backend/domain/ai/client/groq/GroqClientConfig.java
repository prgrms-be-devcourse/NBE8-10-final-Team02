package com.back.backend.domain.ai.client.groq;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
     * HTTP/2 + 커넥션 풀이 적용된 RequestFactory를 생성
     * JdkClientHttpRequestFactory는 HTTP/2 멀티플렉싱과 TLS 세션 재사용을 지원하여
     * 동시 요청 시 SSL 핸드쉐이크 CPU 비용을 대폭 절감함
     */
    private ClientHttpRequestFactory clientHttpRequestFactory(GroqClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(properties.timeout().connect())
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout().read());
        return factory;
    }
}
