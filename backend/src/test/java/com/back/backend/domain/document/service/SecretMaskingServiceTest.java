package com.back.backend.domain.document.service;

import com.back.backend.global.security.GitleaksService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SecretMaskingService 단위 테스트.
 *
 * <p>Gitleaks subprocess 호출 비용이 크므로, 소형 문서(threshold 이하)에서는
 * Gitleaks 호출을 건너뛰는 fast-path 동작을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SecretMaskingServiceTest {

    @Mock
    GitleaksService gitleaksService;

    // =========================================================
    // 소형 문서 fast-path: Gitleaks 호출 없음
    // =========================================================

    @Test
    void mask_skipsGitleaks_whenTextBelowThreshold() {
        // threshold = 100으로 설정, 50자 텍스트 → Gitleaks 스킵
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 100);
        String shortText = "A".repeat(50);

        String result = service.mask(shortText);

        assertThat(result).isEqualTo(shortText); // 원문 그대로 반환
        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }

    @Test
    void mask_skipsGitleaks_whenTextExactlyAtThreshold() {
        // threshold = 100, 100자 → 스킵 (threshold 이하)
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 100);
        String text = "B".repeat(100);

        service.mask(text);

        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }

    // =========================================================
    // 대형 문서: Gitleaks 호출
    // =========================================================

    @Test
    void mask_callsGitleaks_whenTextAboveThreshold() {
        // threshold = 100, 101자 → Gitleaks 호출
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 100);
        String largeText = "C".repeat(101);
        when(gitleaksService.scanTextForMasking(largeText)).thenReturn(List.of());

        service.mask(largeText);

        verify(gitleaksService).scanTextForMasking(largeText);
    }

    @Test
    void mask_redactsSecret_whenGitleaksFindsSecret() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 10);
        String textWithSecret = "token=ghp_secretvalue123456789012345678";
        when(gitleaksService.scanTextForMasking(textWithSecret))
            .thenReturn(List.of("ghp_secretvalue123456789012345678"));

        String result = service.mask(textWithSecret);

        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("ghp_secretvalue123456789012345678");
    }

    // =========================================================
    // 빈 입력 → Gitleaks 호출 없음
    // =========================================================

    @Test
    void mask_returnsNull_whenNullInput() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 2000);

        String result = service.mask(null);

        assertThat(result).isNull();
        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }

    @Test
    void mask_returnsBlank_whenBlankInput() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 2000);

        String result = service.mask("   ");

        assertThat(result).isEqualTo("   ");
        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }

    // =========================================================
    // 기본 threshold 동작 (프로덕션 생성자)
    // =========================================================

    @Test
    void mask_usesDefaultThreshold2000_whenConstructedWithDefaultThreshold() {
        // 기본 threshold = 2000: 1999자 텍스트는 스킵
        SecretMaskingService service = new SecretMaskingService(gitleaksService, 2000);
        String text = "X".repeat(1999);

        service.mask(text);

        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }
}
