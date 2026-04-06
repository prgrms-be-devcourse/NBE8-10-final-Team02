package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

@Component
public class FinalActionDecider {

    private static final Pattern SHORT_PROJECT_SUMMARY_PRESSURE_PATTERN = Pattern.compile(
            "(요청|요구|우선순위).{0,12}(바뀌|달라졌|다시 잡아야 했)"
    );

    private final FollowupRulesProperties properties;

    public FinalActionDecider(FollowupRulesProperties properties) {
        this.properties = properties;
    }

    public FinalAction decide(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution
    ) {
        return decide(questionType, signals, resolution, "");
    }

    public FinalAction decide(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution,
            String normalizedAnswerText
    ) {
        if (isScopeMixed(questionType, signals)) {
            return FinalAction.USE_CANDIDATE;
        }

        if (isLowSpecificity(questionType, signals)) {
            return FinalAction.USE_CANDIDATE;
        }

        if (!resolution.hasMainGap()) {
            return FinalAction.NO_FOLLOW_UP;
        }

        if (isBriefProjectSummary(questionType, resolution, normalizedAnswerText)) {
            return FinalAction.USE_CANDIDATE;
        }

        if (matchesWhitelist(questionType, signals, resolution.exactMainGapSet())) {
            return FinalAction.USE_DYNAMIC;
        }

        return FinalAction.USE_CANDIDATE;
    }

    private boolean isScopeMixed(QuestionType questionType, Map<GapType, Boolean> signals) {
        return questionType == QuestionType.PROJECT
                && Boolean.TRUE.equals(signals.get(GapType.SCOPE_MIXED));
    }

    private boolean isLowSpecificity(QuestionType questionType, Map<GapType, Boolean> signals) {
        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);

        long presentCore = typeRule.getCore().stream()
                .filter(gapType -> Boolean.TRUE.equals(signals.get(gapType)))
                .count();

        long presentMain = Stream.concat(typeRule.getCore().stream(), typeRule.getExtension().stream())
                .distinct()
                .filter(gapType -> Boolean.TRUE.equals(signals.get(gapType)))
                .count();

        return presentMain <= 1 || (presentCore == 0 && presentMain <= 2);
    }

    private boolean isBriefProjectSummary(
            QuestionType questionType,
            GapResolver.Resolution resolution,
            String normalizedAnswerText
    ) {
        if (questionType != QuestionType.PROJECT) {
            return false;
        }

        if (!resolution.exactMainGapSet().equals(Set.of(GapType.RESULT, GapType.REASON))) {
            return false;
        }

        long sentenceCount = normalizedAnswerText.chars()
                .filter(character -> character == '.' || character == '!' || character == '?')
                .count();

        boolean numericBriefWindow = normalizedAnswerText.length() >= 160
                && normalizedAnswerText.length() < 220
                && sentenceCount <= 3;

        return numericBriefWindow
                || isShortNaturalProjectSummary(normalizedAnswerText, sentenceCount)
                || containsBriefProjectVaguenessMarker(normalizedAnswerText);
    }

    private boolean isShortNaturalProjectSummary(String normalizedAnswerText, long sentenceCount) {
        return normalizedAnswerText.length() >= 80
                && normalizedAnswerText.length() < 120
                && sentenceCount == 2
                && normalizedAnswerText.contains("프로젝트")
                && SHORT_PROJECT_SUMMARY_PRESSURE_PATTERN.matcher(normalizedAnswerText).find();
    }

    private boolean containsBriefProjectVaguenessMarker(String normalizedAnswerText) {
        return normalizedAnswerText.contains("추상적")
                || normalizedAnswerText.contains("일반론적")
                || normalizedAnswerText.contains("뭉뚱그려")
                || normalizedAnswerText.contains("두루뭉술");
    }

    private boolean matchesWhitelist(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            Set<GapType> actualMissingGaps
    ) {
        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);

        for (FollowupRulesProperties.DynamicWhitelistRule whitelistRule : typeRule.getDynamicWhitelist()) {
            Set<GapType> expectedMissingGaps = new LinkedHashSet<>(whitelistRule.getMissing());

            boolean missingMatch = properties.getRuntime().isStrictDynamicMode()
                    ? actualMissingGaps.equals(expectedMissingGaps)
                    : actualMissingGaps.containsAll(expectedMissingGaps);

            boolean requireMatch = whitelistRule.getRequire().stream()
                    .allMatch(gapType -> Boolean.TRUE.equals(signals.get(gapType)));

            if (missingMatch && requireMatch) {
                return true;
            }
        }

        return false;
    }
}
