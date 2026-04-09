package com.back.backend.domain.ai.client.vertexai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import java.io.IOException;

/**
 * Vertex AI 연동에 필요한 빈을 등록
 * ai.vertex-ai.project-id가 비어있지 않은 경우에만 활성화
 * 미설정(빈 문자열) 시 빈 자체가 생성되지 않으므로 test/load-test 환경에서 안전
 */
@Configuration
@ConditionalOnExpression("!'${ai.vertex-ai.project-id:}'.isBlank()")
@EnableConfigurationProperties(VertexAiClientProperties.class)
public class VertexAiClientConfig {

    @Bean
    public VertexAiTokenProvider vertexAiTokenProvider(VertexAiClientProperties properties) throws IOException {
        return new VertexAiTokenProvider(properties.credentialsPath());
    }

    @Bean
    public VertexAiClient vertexAiClient(VertexAiClientProperties properties, VertexAiTokenProvider tokenProvider) {
        RestClient restClient = RestClient.builder()
            .baseUrl(properties.buildEndpointUrl())
            .requestFactory(clientHttpRequestFactory(properties))
            .build();

        return new VertexAiClient(restClient, properties, tokenProvider);
    }

    /**
     * HTTP/2 + 커넥션 풀이 적용된 RequestFactory를 생성
     * JdkClientHttpRequestFactory는 HTTP/2 멀티플렉싱과 TLS 세션 재사용을 지원하여
     * 동시 요청 시 SSL 핸드쉐이크 CPU 비용을 대폭 절감함
     */
    private ClientHttpRequestFactory clientHttpRequestFactory(VertexAiClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(properties.timeout().connect())
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout().read());
        return factory;
    }
}
