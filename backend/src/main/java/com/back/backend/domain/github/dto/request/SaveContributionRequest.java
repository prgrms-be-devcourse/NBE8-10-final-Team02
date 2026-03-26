package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /github/contributions/save 요청 바디.
 * 기여 탐색 목록에서 선택한 repo를 저장할 때 사용한다.
 */
public record SaveContributionRequest(
        @NotNull Long githubRepoId,
        @NotBlank String nameWithOwner,
        @NotBlank String url,
        String language,      // null 가능
        Integer repoSizeKb    // null 가능
) {}
