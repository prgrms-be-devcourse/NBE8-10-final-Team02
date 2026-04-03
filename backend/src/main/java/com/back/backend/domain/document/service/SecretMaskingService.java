package com.back.backend.domain.document.service;

import com.back.backend.global.security.GitleaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서에서 추출한 텍스트의 시크릿(API Key, DB Password 등)을 마스킹하는 서비스.
 *
 * <p>{@link GitleaksService}를 이용해 텍스트에서 시크릿 패턴을 탐지하고,
 * 발견된 위치를 {@code [REDACTED]} 로 치환한다.</p>
 *
 * <p>Document는 전체 파일을 제외하는 것이 아니라 텍스트 내 시크릿만 마스킹 후 분석을 계속 진행한다.</p>
 *
 * <h3>실패 시 동작 (Graceful Degradation)</h3>
 * <p>Gitleaks 실행 실패 또는 타임아웃 시 경고 로그만 남기고 원문 텍스트를 그대로 반환한다.
 * 분석 파이프라인은 중단되지 않는다.</p>
 *
 * <h3>보안 로그 원칙</h3>
 * <p>탐지된 시크릿의 실제 값은 절대 로그에 남기지 않는다.
 * 로그에는 발견 건수와 ruleId만 기록한다.</p>
 */
@Service
public class SecretMaskingService {

    private static final Logger log = LoggerFactory.getLogger(SecretMaskingService.class);

    /**
     * 마스킹 플레이스홀더.
     * "[REDACTED:ruleId]" 형식으로 어떤 룰에 의해 탐지되었는지 추적 가능하게 한다.
     */
    private static final String REDACTED_PREFIX = "[REDACTED:";
    private static final String REDACTED_SUFFIX = "]";

    /**
     * Gitleaks 리포트에서 match 값(실제 시크릿) 추출을 위한 임시 스캔에 사용.
     * GitleaksService는 match 값을 반환하지 않으므로, 패턴 기반 정규식으로 마스킹한다.
     *
     * 지원 패턴:
     * - key=value, key: value 형식 (따옴표 유무 무관)
     * - Bearer/token 형식 헤더값
     */
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
        // API Key 할당: key=value, key: "value", key = 'value'
        new SecretPattern(
            Pattern.compile(
                "(?i)(?:api[_-]?key|secret[_-]?key|access[_-]?key|auth[_-]?token|private[_-]?key)"
                + "\\s*[=:]\\s*['\"]?([A-Za-z0-9+/=_\\-]{20,})['\"]?",
                Pattern.MULTILINE
            ), 1
        ),
        // Password 할당
        new SecretPattern(
            Pattern.compile(
                "(?i)(?:password|passwd|pwd)\\s*[=:]\\s*['\"]?([^\\s'\"]{8,})['\"]?",
                Pattern.MULTILINE
            ), 1
        ),
        // Bearer token
        new SecretPattern(
            Pattern.compile(
                "(?i)Bearer\\s+([A-Za-z0-9+/=_\\-.]{20,})",
                Pattern.MULTILINE
            ), 1
        ),
        // DB connection string with password
        new SecretPattern(
            Pattern.compile(
                "(?i)(?:jdbc:[^:]+://[^:]+:[^@]+:)([^@/\\s]+)@",
                Pattern.MULTILINE
            ), 1
        )
    );

    private final GitleaksService gitleaksService;

    public SecretMaskingService(GitleaksService gitleaksService) {
        this.gitleaksService = gitleaksService;
    }

    /**
     * 텍스트에서 시크릿을 탐지하고 마스킹된 텍스트를 반환한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>Gitleaks {@code --no-git} 스캔으로 시크릿 포함 여부 확인</li>
     *   <li>시크릿 발견 시: 정규식 패턴 기반으로 시크릿 값을 {@code [REDACTED:ruleId]}로 치환</li>
     *   <li>Gitleaks 실패 시: 정규식 패턴만으로 폴백 마스킹</li>
     * </ol>
     *
     * @param text 원본 텍스트 (null 또는 빈 값이면 그대로 반환)
     * @return 시크릿이 마스킹된 텍스트
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // Gitleaks로 시크릿 포함 여부 탐지
        GitleaksService.GitleaksScanResult scanResult;
        try {
            scanResult = gitleaksService.scanText(text);
        } catch (Exception e) {
            log.warn("Gitleaks text scan failed, falling back to regex masking: {}", e.getMessage());
            return applyRegexMasking(text, null);
        }

        if (!scanResult.hasFindings()) {
            return text;
        }

        // 시크릿 발견: ruleId 목록 로그 (실제 값 로그 금지)
        List<String> ruleIds = scanResult.findings().stream()
                .map(GitleaksService.SecretFinding::ruleId)
                .distinct()
                .toList();
        log.warn("Secret(s) detected in document text. Count={}, ruleIds={}. Masking applied.",
                scanResult.findings().size(), ruleIds);

        return applyRegexMasking(text, ruleIds);
    }

    /**
     * 정규식 패턴 기반으로 시크릿 값 부분을 {@code [REDACTED:ruleId]} 로 치환한다.
     *
     * <p>Gitleaks는 발견된 시크릿의 정확한 위치(offset)를 반환하지 않으므로,
     * 미리 정의된 패턴으로 시크릿 값이 포함될 수 있는 부분을 치환한다.</p>
     *
     * @param text    원본 텍스트
     * @param ruleIds 탐지된 ruleId 목록 (null이면 "secret"으로 표시)
     * @return 마스킹된 텍스트
     */
    private String applyRegexMasking(String text, List<String> ruleIds) {
        String label = (ruleIds == null || ruleIds.isEmpty()) ? "secret" : String.join(",", ruleIds);
        String replacement = REDACTED_PREFIX + label + REDACTED_SUFFIX;

        String result = text;
        for (SecretPattern sp : SECRET_PATTERNS) {
            Matcher matcher = sp.pattern().matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                // group(sp.valueGroup())이 실제 시크릿 값 — replacement로 치환
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(
                                result.substring(matcher.start(), matcher.start(sp.valueGroup()))
                                + replacement
                        )
                );
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    private record SecretPattern(Pattern pattern, int valueGroup) {}
}
