package com.back.backend.domain.ai.client.stub;

import com.back.backend.domain.ai.client.AiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * load-test profile 전용 AI 클라이언트 빈 등록.
 *
 * GeminiClientConfig, GroqClientConfig는 @Profile("!load-test")로 비활성화되므로
 * 이 Config에서 동일 Bean 이름으로 Stub 구현체를 등록한다.
 * AiClientConfig(AiClientRouter 조립)는 List<AiClient>를 주입받으므로 변경 불필요.
 */
@Configuration
@Profile("load-test")
public class StubAiClientConfig {

    @Bean
    public AiClient geminiClient() {
        return new StubGeminiClient();
    }

    @Bean
    public AiClient groqClient() {
        return new StubGroqClient();
    }
}
