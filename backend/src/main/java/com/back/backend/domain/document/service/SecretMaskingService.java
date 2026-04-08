package com.back.backend.domain.document.service;

import com.back.backend.global.security.GitleaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <h3>소형 문서 fast-path</h3>
 * <p>Gitleaks subprocess 기동 비용(500ms~2s)이 크므로, 텍스트 길이가
 * {@code app.secret-masking.small-doc-skip-threshold-chars}(기본 2000자) 이하인 경우
 * Gitleaks 호출을 건너뛴다. 소형 이력서/메모에서 프로덕션 시크릿이 포함될 가능성이 낮아
 * 보안 위험도 대비 성능 이득이 크다.</p>
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

    /**
     * 이 길이 이하인 텍스트는 Gitleaks 없이 빠르게 통과한다.
     * 소형 문서(이력서, 간단한 메모)에서 프로덕션 시크릿이 포함될 가능성이 낮아
     * Gitleaks subprocess 기동 비용보다 이득이 크다.
     */
    private final int skipThresholdChars;

    /**
     * 프로덕션 생성자: Spring이 설정값을 주입한다.
     */
    public SecretMaskingService(
            GitleaksService gitleaksService,
            @Value("${app.secret-masking.small-doc-skip-threshold-chars:2000}") int skipThresholdChars) {
        this.gitleaksService = gitleaksService;
        this.skipThresholdChars = skipThresholdChars;
    }

    /**
     * 텍스트에서 시크릿을 탐지하고 발견된 값을 {@code [REDACTED]}로 치환한다.
     *
     * <p>텍스트 길이가 threshold 이하면 Gitleaks 호출을 건너뛰고 원문을 반환한다.</p>
     *
     * @param text 원본 텍스트 (null 또는 빈 값이면 그대로 반환)
     * @return 시크릿이 마스킹된 텍스트
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // 소형 문서 fast-path: Gitleaks subprocess 기동 비용 절감
        if (text.length() <= skipThresholdChars) {
            log.debug("Skipping Gitleaks scan for small document ({} chars ≤ {} threshold)",
                text.length(), skipThresholdChars);
            return text;
        }

        List<String> secretValues = gitleaksService.scanTextForMasking(text);

        if (secretValues.isEmpty()) {
            return text;
        }

        // 동일 값이 중복 보고된 경우 불필요한 replace 반복을 방지한다.
        // Gitleaks는 같은 토큰이 여러 위치에서 매칭되면 동일 값을 여러 번 반환할 수 있다.
        List<String> distinct = secretValues.stream().distinct().toList();
        log.warn("Masking {} distinct secret value(s) in document text.", distinct.size());

        String result = text;
        for (String secret : distinct) {
            result = result.replace(secret, REDACTED);
        }
        return result;
    }
}
