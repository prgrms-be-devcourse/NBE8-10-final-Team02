package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GapResolver {

    private static final Set<GapType> POSITIVE_ONLY_FLAGS = EnumSet.of(GapType.SCOPE_MIXED);

    private final FollowupRulesProperties properties;

    public GapResolver(FollowupRulesProperties properties) {
        this.properties = properties;
    }

    public Resolution resolve(QuestionType questionType, Map<GapType, Boolean> signals) {
        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);

        List<GapType> mainMissingGaps = new ArrayList<>();
        appendMissing(mainMissingGaps, typeRule.getCore(), signals);
        appendMissing(mainMissingGaps, typeRule.getExtension(), signals);

        List<GapType> orderedMissingGaps = new ArrayList<>(mainMissingGaps);
        appendSupportedSoftMissing(orderedMissingGaps, typeRule.getSoft(), signals);

        GapType primaryGap = orderedMissingGaps.isEmpty() ? null : orderedMissingGaps.get(0);
        GapType secondaryGap = orderedMissingGaps.size() > 1 ? orderedMissingGaps.get(1) : null;

        return new Resolution(
                List.copyOf(mainMissingGaps),
                List.copyOf(orderedMissingGaps),
                primaryGap,
                secondaryGap
        );
    }

    private void appendMissing(List<GapType> bucket, List<GapType> priorities, Map<GapType, Boolean> signals) {
        for (GapType gapType : priorities) {
            if (!Boolean.TRUE.equals(signals.get(gapType))) {
                bucket.add(gapType);
            }
        }
    }

    private void appendSupportedSoftMissing(List<GapType> bucket, List<GapType> softGaps, Map<GapType, Boolean> signals) {
        for (GapType gapType : softGaps) {
            if (POSITIVE_ONLY_FLAGS.contains(gapType)) {
                continue;
            }
            if (!properties.hasSignalPattern(gapType)) {
                continue;
            }
            if (!Boolean.TRUE.equals(signals.get(gapType))) {
                bucket.add(gapType);
            }
        }
    }

    public record Resolution(
            List<GapType> mainMissingGaps,
            List<GapType> orderedMissingGaps,
            GapType primaryGap,
            GapType secondaryGap
    ) {
        public boolean hasMainGap() {
            return !mainMissingGaps.isEmpty();
        }

        public Set<GapType> exactMainGapSet() {
            return new LinkedHashSet<>(mainMissingGaps);
        }
    }
}
