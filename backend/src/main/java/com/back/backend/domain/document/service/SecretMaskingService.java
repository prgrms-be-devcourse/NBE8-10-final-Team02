package com.back.backend.domain.document.service;

import com.back.backend.global.security.GitleaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 문서에서 추출한 텍스트의 시크릿(API Key, DB Password 등)을 마스킹하는 서비스.
 *
 * <p>Gitleaks를 단일 탐지 엔진으로 사용한다. Gitleaks가 탐지한 실제 매칭 값을
 * {@code [REDACTED]} 로 치환하며, 별도의 정규식 패턴을 관리하지 않는다.</p>
 *
 * <p>이 방식의 장점: Gitleaks 룰셋이 업데이트될수록 자동으로 커버 범위가 넓어진다.</p>
 *
 * <h3>실패 시 동작 (Graceful Degradation)</h3>
 * <p>Gitleaks 실행 실패 또는 타임아웃 시 경고 로그만 남기고 원문 텍스트를 그대로 반환한다.</p>
 *
 * <h3>보안 원칙</h3>
 * <p>탐지된 시크릿의 실제 값은 절대 로그에 남기지 않는다.</p>
 */
@Service
public class SecretMaskingService {

    private static final Logger log = LoggerFactory.getLogger(SecretMaskingService.class);

    private static final String REDACTED = "[REDACTED]";

    private final GitleaksService gitleaksService;

    public SecretMaskingService(GitleaksService gitleaksService) {
        this.gitleaksService = gitleaksService;
    }

    /**
     * 텍스트에서 시크릿을 탐지하고 발견된 값을 {@code [REDACTED]}로 치환한다.
     *
     * @param text 원본 텍스트 (null 또는 빈 값이면 그대로 반환)
     * @return 시크릿이 마스킹된 텍스트
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        List<String> secretValues = gitleaksService.scanTextForMasking(text);

        if (secretValues.isEmpty()) {
            return text;
        }

        log.warn("Masking {} secret value(s) in document text.", secretValues.size());

        String result = text;
        for (String secret : secretValues) {
            result = result.replace(secret, REDACTED);
        }
        return result;
    }
}
