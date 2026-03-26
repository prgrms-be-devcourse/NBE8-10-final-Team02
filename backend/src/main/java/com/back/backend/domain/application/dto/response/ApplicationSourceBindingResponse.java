package com.back.backend.domain.application.dto.response;

import java.util.List;

public record ApplicationSourceBindingResponse(
        Long applicationId,
        List<Long> repositoryIds,
        List<Long> documentIds,
        int sourceCount
) {
}
