package com.back.backend.domain.ai.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI rate limit 한도 설정 (application.yml의 ai.rate-limit 블록)
 * 각 provider별 RPM/RPD/TPM/TPD 한도를 담는다
 * tpd가 null이면 일간 토큰 체크를 비활성화한다
 */
@ConfigurationProperties(prefix = "ai.rate-limit")
public class AiRateLimitProperties {

    private ProviderLimit gemini = new ProviderLimit();
    private ProviderLimit groq = new ProviderLimit();
    private ProviderLimit vertexAi = new ProviderLimit();

    /**
     * provider 코드로 해당 provider의 한도 설정을 반환
     * 알 수 없는 코드면 기본값(모든 한도 MAX)을 반환
     *
     * @param providerCode AiProvider.getValue() 반환값 (예: "gemini", "groq", "vertex-ai")
     * @return 해당 provider의 한도 설정
     */
    public ProviderLimit getFor(String providerCode) {
        if ("gemini".equals(providerCode)) {
            return gemini;
        } else if ("groq".equals(providerCode)) {
            return groq;
        } else if ("vertex-ai".equals(providerCode)) {
            return vertexAi;
        }
        // 알 수 없는 provider는 기본값(무제한)으로 처리
        return new ProviderLimit();
    }

    public ProviderLimit getGemini() {
        return gemini;
    }

    public void setGemini(ProviderLimit gemini) {
        this.gemini = gemini;
    }

    public ProviderLimit getGroq() {
        return groq;
    }

    public void setGroq(ProviderLimit groq) {
        this.groq = groq;
    }

    public ProviderLimit getVertexAi() {
        return vertexAi;
    }

    public void setVertexAi(ProviderLimit vertexAi) {
        this.vertexAi = vertexAi;
    }

    /**
     * 개별 provider의 rate limit 한도 설정
     * 기본값: rpm/rpd/tpm = Integer.MAX_VALUE (무제한), tpd = null (비활성화)
     */
    public static class ProviderLimit {

        /** 분당 요청 수 한도 */
        private int rpm = Integer.MAX_VALUE;
        /** 일간 요청 수 한도 */
        private int rpd = Integer.MAX_VALUE;
        /** 분당 토큰 수 한도 */
        private int tpm = Integer.MAX_VALUE;
        /** 일간 토큰 수 한도. null이면 일간 토큰 체크 비활성화. */
        private Integer tpd = null;

        /** 일간 토큰 한도가 설정되어 있는지 여부 */
        public boolean hasTpd() {
            return tpd != null;
        }

        public int getRpm() {
            return rpm;
        }

        public void setRpm(int rpm) {
            this.rpm = rpm;
        }

        public int getRpd() {
            return rpd;
        }

        public void setRpd(int rpd) {
            this.rpd = rpd;
        }

        public int getTpm() {
            return tpm;
        }

        public void setTpm(int tpm) {
            this.tpm = tpm;
        }

        public Integer getTpd() {
            return tpd;
        }

        public void setTpd(Integer tpd) {
            this.tpd = tpd;
        }
    }
}
