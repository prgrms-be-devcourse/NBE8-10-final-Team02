package com.back.backend.domain.followup.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TextNormalizer {

    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        return raw
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
