package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 여러 repo를 한 번에 배치 분석 요청하는 DTO.
 *
 * <p>사용 예:
 * <pre>
 * POST /api/v1/github/repositories/analyze-batch
 * { "repositoryIds": [1, 2, 3] }
 * </pre>
 *
 * @param repositoryIds 분석할 github_repositories.id 목록 (1개 이상, 최대 10개)
 */
public record BatchAnalyzeRequest(
        @NotEmpty(message = "분석할 저장소 목록은 비어있을 수 없습니다.")
        @Size(max = 10, message = "한 번에 최대 10개 저장소를 분석할 수 있습니다.")
        List<Long> repositoryIds
) {}
