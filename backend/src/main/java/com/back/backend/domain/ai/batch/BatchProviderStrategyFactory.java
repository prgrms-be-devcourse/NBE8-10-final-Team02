package com.back.backend.domain.ai.batch;

import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배치 AI 호출에 사용할 {@link BatchProviderStrategy}를 결정한다.
 *
 * <p>결정 로직:
 * <ol>
 *   <li>{@code preferredProvider}가 {@link AiProvider#VERTEX_AI}이고
 *       실제로 등록되어 있으면 → {@link VertexAiStrategy}</li>
 *   <li>미등록이거나 다른 provider이면 → {@link GeminiFreeStrategy} (fallback)</li>
 * </ol>
 */
@Component
public class BatchProviderStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(BatchProviderStrategyFactory.class);

    private final AiClientRouter router;

    public BatchProviderStrategyFactory(AiClientRouter router) {
        this.router = router;
    }

    /**
     * 배치 템플릿의 preferredProvider를 기준으로 전략을 결정한다.
     *
     * @param preferredProvider 템플릿에 설정된 AI provider
     * @return 실제 사용할 배치 전략
     */
    public BatchProviderStrategy resolve(AiProvider preferredProvider) {
        if (preferredProvider == AiProvider.VERTEX_AI) {
            try {
                router.getClient(AiProvider.VERTEX_AI); // 등록 여부만 확인
                return new VertexAiStrategy();
            } catch (IllegalArgumentException e) {
                log.warn("[BatchProviderStrategy] VERTEX_AI 미등록 → GeminiFreeStrategy로 fallback");
            }
        }
        return new GeminiFreeStrategy();
    }
}
