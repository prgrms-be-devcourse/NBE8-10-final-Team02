package com.back.backend.domain.document.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextSanitizationService 단위 테스트.
 *
 * <p>OCR 아티팩트 정제가 올바르게 동작하는지 검증한다.
 * 특히 Gitleaks가 시크릿을 탐지할 수 있도록 패턴을 깨뜨리는 문자들이
 * 제거/치환되는지 확인하는 데 집중한다.</p>
 */
class TextSanitizationServiceTest {

    private final TextSanitizationService sut = new TextSanitizationService();

    // =========================================================
    // null / 빈 입력
    // =========================================================

    @ParameterizedTest
    @NullAndEmptySource
    void sanitize_returnsInputUnchangedForNullOrEmpty(String input) {
        assertThat(sut.sanitize(input)).isEqualTo(input);
    }

    // =========================================================
    // 줄바꿈 정규화 — Gitleaks 탐지의 핵심
    // =========================================================

    @Test
    void sanitize_normalizesCRLF_toNewline() {
        // password=secret\r\n 같은 패턴이 Gitleaks에서 탐지되도록 \r 제거
        String input = "password=mySecret123\r\nsome text";
        assertThat(sut.sanitize(input)).isEqualTo("password=mySecret123\nsome text");
    }

    @Test
    void sanitize_normalizesCR_toNewline() {
        String input = "api_key=ABCDEF12345\rother text";
        assertThat(sut.sanitize(input)).isEqualTo("api_key=ABCDEF12345\nother text");
    }

    @Test
    void sanitize_preservesExistingLF() {
        String input = "line1\nline2\nline3";
        assertThat(sut.sanitize(input)).isEqualTo("line1\nline2\nline3");
    }

    // =========================================================
    // Zero-width 문자 제거 — Gitleaks 탐지 우회 방지
    // =========================================================

    @Test
    void sanitize_removesZeroWidthSpace() {
        // ghp_W\u200BZR... 처럼 토큰 중간에 삽입된 zero-width space 제거
        String input = "ghp_W\u200BZRitK71WHBiuUfKsy2VJ3Kv";
        assertThat(sut.sanitize(input)).isEqualTo("ghp_WZRitK71WHBiuUfKsy2VJ3Kv");
    }

    @Test
    void sanitize_removesZeroWidthNonJoiner() {
        String input = "AIAB\u200CCDEFsecret123";
        assertThat(sut.sanitize(input)).isEqualTo("AIABCDEFsecret123");
    }

    @Test
    void sanitize_removesZeroWidthJoiner() {
        String input = "secret\u200Dvalue";
        assertThat(sut.sanitize(input)).isEqualTo("secretvalue");
    }

    @Test
    void sanitize_removesByteOrderMark() {
        String input = "\uFEFFapi_key=some-secret-value";
        assertThat(sut.sanitize(input)).isEqualTo("api_key=some-secret-value");
    }

    @Test
    void sanitize_removesMultipleZeroWidthCharactersInToken() {
        // OCR이 토큰 여러 곳에 zero-width 문자를 삽입한 경우
        String input = "github\u200B_pat\u200C_11ABCDEF\u200D1234567890";
        assertThat(sut.sanitize(input)).isEqualTo("github_pat_11ABCDEF1234567890");
    }

    // =========================================================
    // Soft hyphen 제거
    // =========================================================

    @Test
    void sanitize_removesSoftHyphen() {
        // OCR이 긴 단어를 줄바꿈할 때 soft hyphen을 삽입하는 경우
        String input = "pass\u00ADword=se\u00ADcret123";
        assertThat(sut.sanitize(input)).isEqualTo("password=secret123");
    }

    // =========================================================
    // Non-breaking space 치환
    // =========================================================

    @Test
    void sanitize_replacesNonBreakingSpace() {
        String input = "api_key\u00A0=\u00A0SECRET_VALUE";
        assertThat(sut.sanitize(input)).isEqualTo("api_key = SECRET_VALUE");
    }

    @Test
    void sanitize_replacesNarrowNonBreakingSpace() {
        String input = "token\u202FVALUE";
        assertThat(sut.sanitize(input)).isEqualTo("token VALUE");
    }

    // =========================================================
    // 연속 빈 줄 축소
    // =========================================================

    @Test
    void sanitize_collapsesExcessiveBlankLines() {
        String input = "line1\n\n\n\n\nline2";
        assertThat(sut.sanitize(input)).isEqualTo("line1\n\nline2");
    }

    @Test
    void sanitize_preservesTwoConsecutiveBlankLines() {
        String input = "line1\n\n\nline2";
        assertThat(sut.sanitize(input)).isEqualTo("line1\n\nline2");
    }

    // =========================================================
    // 복합 시나리오 — OCR 실제 아티팩트를 시뮬레이션
    // =========================================================

    @Test
    void sanitize_cleansUpRealWorldOcrArtifact_githubPatToken() {
        // OCR이 GitHub PAT 토큰을 \r\n + zero-width 문자와 함께 추출한 경우
        // Gitleaks가 "github_pat_11ABCDEF1234567890..." 을 온전히 탐지할 수 있어야 함
        String raw = "token: github\u200B_pat\u200C_11ABCDEF1234567890abcdefghijk\r\n";
        String cleaned = sut.sanitize(raw);

        assertThat(cleaned).isEqualTo("token: github_pat_11ABCDEF1234567890abcdefghijk\n");
        assertThat(cleaned).doesNotContain("\u200B", "\u200C", "\r");
    }

    @Test
    void sanitize_cleansUpRealWorldOcrArtifact_passwordLine() {
        // 비밀번호 뒤에 \r\n 이 붙어 Gitleaks가 탐지 못 하는 케이스
        String raw = "SPRING_DATASOURCE_PASSWORD=myStrongP@ssw0rd!\r\nSPRING_JPA_SHOW_SQL=false";
        String cleaned = sut.sanitize(raw);

        assertThat(cleaned).isEqualTo(
                "SPRING_DATASOURCE_PASSWORD=myStrongP@ssw0rd!\nSPRING_JPA_SHOW_SQL=false");
        assertThat(cleaned).doesNotContain("\r");
    }

    @Test
    void sanitize_normalTextIsUnchangedByUnicodeNormalization() {
        // 일반 텍스트는 변환 없이 그대로 유지
        String input = "이력서\n홍길동\n010-1234-5678";
        assertThat(sut.sanitize(input)).isEqualTo("이력서\n홍길동\n010-1234-5678");
    }
}
