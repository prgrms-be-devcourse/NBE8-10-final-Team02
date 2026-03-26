package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PUT /github/repositories/selection 요청 바디.
 * 선택할 repo ID 목록을 전달한다. 빈 리스트면 전체 해제.
 */
public record RepositorySelectionRequest(

        @NotNull
        @Size(max = 100, message = "한 번에 최대 100개까지 선택할 수 있습니다.")
        List<Long> repositoryIds
) {}