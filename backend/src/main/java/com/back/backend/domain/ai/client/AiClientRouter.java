package com.back.backend.domain.ai.client;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ai.provider 설정 값에 따라 적절한 AiClient 구현체를 선택
 * 새 provider 추가 시 AiClient 구현체만 빈으로 등록하면 자동으로 라우팅
 * fallback provider가 설정되면 기본 provider 실패 시 대체 호출 가능
 */
public class AiClientRouter {

    private final Map<AiProvider, AiClient> clients;
    private final AiProvider defaultProvider;
    private final AiProvider fallbackProvider; // null이면 fallback 비활성화

    /**
     * @param clients          등록된 모든 AiClient 구현체 목록, Spring이 자동 주입
     * @param defaultProvider  application.yml의 ai.provider 값
     * @param fallbackProvider application.yml의 ai.fallback-provider 값 (null 허용)
     */
    public AiClientRouter(List<AiClient> clients, AiProvider defaultProvider, AiProvider fallbackProvider) {
        this.clients = clients.stream()
            .collect(Collectors.toMap(AiClient::getProvider, Function.identity()));
        this.defaultProvider = defaultProvider;
        this.fallbackProvider = fallbackProvider;
    }

    /**
     * 기본 provider의 AiClient를 반환
     */
    public AiClient getDefault() {
        return getClient(defaultProvider);
    }

    /**
     * fallback provider의 AiClient를 반환
     * 설정되지 않았으면 빈 Optional 반환
     */
    public Optional<AiClient> getFallback() {
        if (fallbackProvider == null) {
            return Optional.empty();
        }
        return Optional.of(getClient(fallbackProvider));
    }

    /**
     * 등록된 모든 AiClient 구현체를 반환
     * AiStatusService에서 전체 provider 상태 조회 시 사용
     */
    public Collection<AiClient> getAllClients() {
        return Collections.unmodifiableCollection(clients.values());
    }

    /**
     * 특정 provider의 AiClient를 반환
     * fallback 등에서 provider를 명시적으로 지정할 때 사용
     */
    public AiClient getClient(AiProvider provider) {
        AiClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("등록되지 않은 AI provider: " + provider);
        }
        return client;
    }
}
