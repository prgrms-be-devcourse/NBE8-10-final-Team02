package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CandidateQuestionSelector {

    private final FollowupRulesProperties properties;

    public CandidateQuestionSelector(FollowupRulesProperties properties) {
        this.properties = properties;
    }

    public List<CandidateQuestionType> select(
            QuestionType questionType,
            Map<GapType, Boolean> signals,
            GapResolver.Resolution resolution,
            FinalAction finalAction
    ) {
        if (finalAction != FinalAction.USE_CANDIDATE) {
            return List.of();
        }

        FollowupRulesProperties.TypeRule typeRule = properties.requiredTypeRule(questionType);
        Set<CandidateQuestionType> selected = new LinkedHashSet<>();

        if (questionType == QuestionType.PROJECT && Boolean.TRUE.equals(signals.get(GapType.SCOPE_MIXED))) {
            CandidateQuestionType scopeCandidate = typeRule.getCandidateMap().get(GapType.SCOPE_MIXED);
            if (scopeCandidate != null) {
                selected.add(scopeCandidate);
            }
        }

        for (GapType gapType : resolution.orderedMissingGaps()) {
            CandidateQuestionType mapped = typeRule.getCandidateMap().get(gapType);
            if (mapped != null) {
                selected.add(mapped);
                continue;
            }

            if (typeRule.getCore().contains(gapType) && typeRule.getCoreFallback() != null) {
                selected.add(typeRule.getCoreFallback());
            }
        }

        return selected.stream()
                .limit(properties.getRuntime().getMaxCandidateQuestionTypes())
                .toList();
    }
}
