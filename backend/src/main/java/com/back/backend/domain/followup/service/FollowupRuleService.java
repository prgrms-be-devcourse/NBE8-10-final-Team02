package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FollowupRuleService {

    private final TextNormalizer textNormalizer;
    private final SignalExtractor signalExtractor;
    private final GapResolver gapResolver;
    private final FinalActionDecider finalActionDecider;
    private final CandidateQuestionSelector candidateQuestionSelector;

    public FollowupRuleService(
            TextNormalizer textNormalizer,
            SignalExtractor signalExtractor,
            GapResolver gapResolver,
            FinalActionDecider finalActionDecider,
            CandidateQuestionSelector candidateQuestionSelector
    ) {
        this.textNormalizer = textNormalizer;
        this.signalExtractor = signalExtractor;
        this.gapResolver = gapResolver;
        this.finalActionDecider = finalActionDecider;
        this.candidateQuestionSelector = candidateQuestionSelector;
    }

    public FollowupAnalyzeResponse analyze(FollowupAnalyzeRequest request) {
        String normalizedAnswerText = textNormalizer.normalize(request.answerText());
        Map<GapType, Boolean> signals = signalExtractor.extract(normalizedAnswerText);
        GapResolver.Resolution resolution = gapResolver.resolve(request.questionType(), signals);
        FinalAction finalAction = finalActionDecider.decide(request.questionType(), signals, resolution);
        List<CandidateQuestionType> candidateQuestionTypes = candidateQuestionSelector.select(
                request.questionType(),
                signals,
                resolution,
                finalAction
        );

        return new FollowupAnalyzeResponse(
                request.questionType(),
                signals,
                resolution.orderedMissingGaps(),
                resolution.primaryGap(),
                resolution.secondaryGap(),
                finalAction,
                candidateQuestionTypes
        );
    }
}
