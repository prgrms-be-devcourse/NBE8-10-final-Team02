package com.back.backend.domain.followup.dto.response;

import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;

import java.util.List;
import java.util.Map;

public record FollowupAnalyzeResponse(
        QuestionType questionType,
        Map<GapType, Boolean> signals,
        List<GapType> orderedMissingGaps,
        GapType primaryGap,
        GapType secondaryGap,
        FinalAction finalAction,
        List<CandidateQuestionType> candidateQuestionTypes
) {
}
