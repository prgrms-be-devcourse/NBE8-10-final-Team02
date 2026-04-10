package com.back.backend.domain.activity.dto;

import java.util.List;

public record ActivityStatsResponse(
        List<ScoreTrendEntry> scoreTrend,
        List<WeakAreaEntry> weakAreas,
        List<WeakAreaEntry> feedbackWeakAreas
) {
}
