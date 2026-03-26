package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /github/contributions/add-by-url 요청 바디.
 * 사용자가 직접 입력한 GitHub repo URL로 기여 repo를 추가할 때 사용한다.
 * 본인 커밋이 존재하는 경우에만 저장된다.
 */
public record AddContributionByUrlRequest(
        @NotBlank String url
) {}
