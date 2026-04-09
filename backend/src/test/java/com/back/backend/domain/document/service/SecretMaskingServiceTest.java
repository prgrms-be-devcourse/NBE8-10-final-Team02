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

@ExtendWith(MockitoExtension.class)
class SecretMaskingServiceTest {

    @Mock
    GitleaksService gitleaksService;

    @Test
    void mask_redactsSecret_whenGitleaksFindsSecret() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService);
        String textWithSecret = "token=ghp_secretvalue123456789012345678";
        when(gitleaksService.scanTextForMasking(textWithSecret))
            .thenReturn(List.of("ghp_secretvalue123456789012345678"));

        String result = service.mask(textWithSecret);

        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("ghp_secretvalue123456789012345678");
    }

    @Test
    void mask_returnsOriginal_whenGitleaksFindsNothing() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService);
        String text = "Hello world, no secrets here.";
        when(gitleaksService.scanTextForMasking(text)).thenReturn(List.of());

        String result = service.mask(text);

        assertThat(result).isEqualTo(text);
    }

    @Test
    void mask_deduplicatesSecrets_whenGitleaksReturnsDuplicates() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService);
        String secret = "ghp_secretvalue123456789012345678";
        String text = "token=" + secret + " and again " + secret;
        when(gitleaksService.scanTextForMasking(text)).thenReturn(List.of(secret, secret));

        String result = service.mask(text);

        assertThat(result).doesNotContain(secret);
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    void mask_returnsNull_whenNullInput() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService);

        String result = service.mask(null);

        assertThat(result).isNull();
        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }

    @Test
    void mask_returnsBlank_whenBlankInput() {
        SecretMaskingService service = new SecretMaskingService(gitleaksService);

        String result = service.mask("   ");

        assertThat(result).isEqualTo("   ");
        verify(gitleaksService, never()).scanTextForMasking(anyString());
    }
}
