package com.back.backend.domain.application.dto.request;

import java.util.List;

public record SaveApplicationSourcesRequest(
        List<Long> repositoryIds,
        List<Long> documentIds
) {

    public List<Long> repositoryIdsOrEmpty() {
        return repositoryIds == null ? List.of() : repositoryIds;
    }

    public List<Long> documentIdsOrEmpty() {
        return documentIds == null ? List.of() : documentIds;
    }
}
