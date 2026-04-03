package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 여러 repo의 배치 분석을 한 번에 취소하는 요청 DTO.
 *
 * <p>사용 예:
 * <pre>
 * DELETE /api/v1/github/repositories/analyze-batch
 * { "repositoryIds": [1, 2, 3] }
 * </pre>
 *
 * @param repositoryIds 취소할 github_repositories.id 목록 (1개 이상, 최대 10개)
 */
public record BatchCancelRequest(
        @NotEmpty(message = "취소할 저장소 목록은 비어있을 수 없습니다.")
        @Size(max = 10, message = "한 번에 최대 10개 저장소를 취소할 수 있습니다.")
        List<Long> repositoryIds
) {}
