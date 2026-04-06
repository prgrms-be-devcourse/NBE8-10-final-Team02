package com.back.backend.domain.document.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * OCR 추출 텍스트를 Gitleaks/PII 마스킹 파이프라인에 넘기기 전에 정제하는 서비스.
 *
 * <p>OCR 엔진은 다음과 같은 아티팩트를 포함한 텍스트를 생성한다:</p>
 * <ul>
 *   <li>{@code \r\n} 줄바꿈 — 시크릿 패턴을 깨뜨림 (예: {@code password=secret\r\n})</li>
 *   <li>Zero-width 문자 — 토큰 중간에 삽입돼 Gitleaks 탐지 우회 (예: {@code ghp_W\u200BZR...})</li>
 *   <li>Non-breaking space — 공백 패턴 오작동</li>
 *   <li>Soft hyphen — 단어를 인위적으로 분리</li>
 * </ul>
 *
 * <h3>정제 순서</h3>
 * <ol>
 *   <li>Unicode NFC 정규화 (합성 문자 통일)</li>
 *   <li>Zero-width 문자 제거 (U+200B/C/D, U+FEFF)</li>
 *   <li>Soft hyphen 제거 (U+00AD)</li>
 *   <li>Non-breaking space → 일반 공백 치환 (U+00A0, U+202F, U+2007)</li>
 *   <li>줄바꿈 정규화 ({@code \r\n}, {@code \r} → {@code \n})</li>
 *   <li>과도한 연속 빈 줄 축소 (3줄 이상 → 2줄)</li>
 * </ol>
 */
@Service
public class TextSanitizationService {

    /** Zero-width 문자: U+200B(ZWSP), U+200C(ZWNJ), U+200D(ZWJ), U+FEFF(BOM/ZWNBS) */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\u200B\u200C\u200D\uFEFF]");

    /** Soft hyphen: U+00AD */
    private static final Pattern SOFT_HYPHEN = Pattern.compile("\u00AD");

    /** Non-breaking spaces: U+00A0, U+202F(NNBSP), U+2007(FIGURE SPACE) */
    private static final Pattern NON_BREAKING_SPACE = Pattern.compile("[\u00A0\u202F\u2007]");

    /** 3줄 이상 연속 빈 줄 */
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("(\n){3,}");

    /**
     * 텍스트를 정제한다.
     *
     * @param text 원본 텍스트 (null 또는 빈 값이면 그대로 반환)
     * @return 정제된 텍스트
     */
    public String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1) Unicode NFC 정규화 (합성/분리 문자 통일)
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);

        // 2) Zero-width 문자 제거 (토큰 중간 삽입 아티팩트)
        result = ZERO_WIDTH.matcher(result).replaceAll("");

        // 3) Soft hyphen 제거
        result = SOFT_HYPHEN.matcher(result).replaceAll("");

        // 4) Non-breaking space → 일반 공백
        result = NON_BREAKING_SPACE.matcher(result).replaceAll(" ");

        // 5) 줄바꿈 정규화 (\r\n, \r → \n)
        result = result.replace("\r\n", "\n").replace("\r", "\n");

        // 6) 과도한 연속 빈 줄 축소 (3줄 이상 → 2줄)
        result = EXCESS_BLANK_LINES.matcher(result).replaceAll("\n\n");

        return result;
    }
}
