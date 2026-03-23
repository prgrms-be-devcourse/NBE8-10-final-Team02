package com.back.backend.domain.github.dto.response;

import java.util.List;

/**
 * PUT /github/repositories/selection 응답 데이터.
 */
public record RepositorySelectionResponse(
        List<Long> selectedRepositoryIds,
        int selectedCount
) {

    public static RepositorySelectionResponse of(List<Long> selectedIds) {
        return new RepositorySelectionResponse(selectedIds, selectedIds.size());
    }
}