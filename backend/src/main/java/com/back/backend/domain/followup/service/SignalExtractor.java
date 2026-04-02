package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.GapType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SignalExtractor {

    private final Map<GapType, List<Pattern>> compiledPatterns = new LinkedHashMap<>();

    public SignalExtractor(FollowupRulesProperties properties) {
        for (GapType gapType : GapType.values()) {
            List<Pattern> patterns = new ArrayList<>();

            for (String regex : properties.getSignals().getOrDefault(gapType, List.of())) {
                patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }

            compiledPatterns.put(gapType, List.copyOf(patterns));
        }
    }

    public Map<GapType, Boolean> extract(String normalizedAnswerText) {
        Map<GapType, List<String>> matchedPatterns = extractMatchedPatterns(normalizedAnswerText);
        Map<GapType, Boolean> signals = new LinkedHashMap<>();

        for (GapType gapType : GapType.values()) {
            signals.put(gapType, !matchedPatterns.getOrDefault(gapType, List.of()).isEmpty());
        }

        return signals;
    }

    Map<GapType, List<String>> extractMatchedPatterns(String normalizedAnswerText) {
        Map<GapType, List<String>> matchedPatterns = new LinkedHashMap<>();

        for (GapType gapType : GapType.values()) {
            List<String> matchedRegexes = compiledPatterns.getOrDefault(gapType, List.of())
                    .stream()
                    .filter(pattern -> pattern.matcher(normalizedAnswerText).find())
                    .map(Pattern::pattern)
                    .toList();
            matchedPatterns.put(gapType, matchedRegexes);
        }

        return matchedPatterns;
    }
}
