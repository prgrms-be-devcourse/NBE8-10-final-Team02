package com.back.backend.ai.client;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ai.provider 설정 값에 따라 적절한 AiClient 구현체를 선택
 * 새 provider 추가 시 AiClient 구현체만 빈으로 등록하면 자동으로 라우팅
 */
public class AiClientRouter {

    private final Map<AiProvider, AiClient> clients;
    private final AiProvider defaultProvider;

    /**
     * @param clients 등록된 모든 AiClient 구현체 목록, Spring이 자동 주입
     * @param defaultProvider application.yml의 ai.provider 값
     */
    public AiClientRouter(List<AiClient> clients, AiProvider defaultProvider) {
        this.clients = clients.stream()
            .collect(Collectors.toMap(AiClient::getProvider, Function.identity()));
        this.defaultProvider = defaultProvider;
    }

    /**
     * 기본 provider의 AiClient를 반환
     */
    public AiClient getDefault() {
        return getClient(defaultProvider);
    }

    /**
     * 특정 provider의 AiClient를 반환
     * fallback 등에서 provider를 명시적으로 지정할 때 사용
     * 나중에 fallback이나 특정 모델 지정시 사용
     */
    public AiClient getClient(AiProvider provider) {
        AiClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("등록되지 않은 AI provider: " + provider);
        }
        return client;
    }
}
