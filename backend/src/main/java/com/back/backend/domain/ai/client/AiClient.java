package com.back.backend.domain.ai.client;

/**
 * AI 모델 호출 공통 인터페이스.
 * 모든 provider 구현체(Gemini, OpenAI, Claude 등)는 이 인터페이스를 구현
 *
 * 파이프라인은 이 인터페이스에만 의존하므로,
 * provider가 바뀌어도 파이프라인 코드는 수정할 필요가 없음
 */
public interface AiClient {

    /**
     * AI 모델에 요청,응답
     *
     * @param request 공통 요청 DTO (프롬프트, temperature 등)
     * @return 공통 응답 DTO (생성 텍스트, 토큰 사용량)
     * @throws AiClientException AI 호출 실패 시
     */
    AiResponse call(AiRequest request);

    /**
     * 이 클라이언트가 어떤 provider인지 반환한다.
     * AiClientRouter에서 provider별 구현체를 찾을 때 사용된다.
     */
    AiProvider getProvider();
}
