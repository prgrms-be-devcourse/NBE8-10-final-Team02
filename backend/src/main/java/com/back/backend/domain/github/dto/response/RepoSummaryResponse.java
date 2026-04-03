package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.entity.RepoSummary;

import java.time.Instant;

/**
 * GET /github/repositories/{id}/summary 응답.
 *
 * data 필드는 portfolio-summary.schema.json 형식의 JSON 문자열을 그대로 전달한다.
 * 프론트엔드에서 JSON.parse() 후 project 객체를 렌더링한다.
 *
 * TODO: 백엔드 응답 스키마 변경 (projects[] -> project 단일 객체).
 *   UI 수정 최소화를 위해 API 레이어(Interceptor 등)에 Adapter 패턴을 적용하여
 *   단일 객체를 배열 형태로 감싸는 정규화 작업 필요.
 */
public record RepoSummaryResponse(
        Long repositoryId,
        int summaryVersion,
        String data,           // portfolio-summary.schema.json 형식 JSON 문자열
        Instant generatedAt
) {

    public static RepoSummaryResponse from(RepoSummary summary) {
        return new RepoSummaryResponse(
                summary.getGithubRepository().getId(),
                summary.getSummaryVersion(),
                summary.getData(),
                summary.getGeneratedAt()
        );
    }
}
