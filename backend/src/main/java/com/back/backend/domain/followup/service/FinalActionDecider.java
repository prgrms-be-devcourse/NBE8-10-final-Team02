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

@Component
public class FinalActionDecider {

    private final FollowupRulesProperties properties;

    public FinalActionDecider(FollowupRulesProperties properties) {
        this.properties = properties;
    }

    public FinalAction decide(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution
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
