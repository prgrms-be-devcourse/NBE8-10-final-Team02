package com.back.backend.domain.github.dto.response;

import com.back.backend.domain.github.entity.GithubRepository;

import java.time.Instant;

/**
 * GET /github/repositories 응답의 개별 repo 항목.
 */
public record GithubRepositoryResponse(
        Long id,
        Long githubRepoId,
        String ownerLogin,
        String repoName,
        String fullName,
        String htmlUrl,
        String visibility,      // "public" | "private" | "internal"
        String defaultBranch,
        boolean isSelected,
        boolean hasCommits,     // 커밋 동기화 완료 여부 (false면 포트폴리오 분석 불가)
        RepoSyncStatusResponse analysisStatus,  // 분석 상태 (분석 미요청 또는 TTL 만료 시 null)
        boolean hasSummary,     // DB에 RepoSummary 레코드가 존재하는지 여부 (Redis TTL과 무관한 영구 상태)
        Instant pushedAt,       // GitHub pushed_at. 기여/URL 추가 경로는 null
        String ownerType,       // "owner" | "collaborator". 저장 시점에 결정됨. 기존 데이터는 null
        String language,        // primary language. null 가능
        String secretExcludedFiles  // 시크릿 스캔으로 제외된 파일 목록 JSON (null이면 발견 없음)
) {

    public static GithubRepositoryResponse from(GithubRepository repo) {
        return from(repo, false, null, false);
    }

    public static GithubRepositoryResponse from(GithubRepository repo, boolean hasCommits) {
        return from(repo, hasCommits, null, false);
    }

    public static GithubRepositoryResponse from(GithubRepository repo, boolean hasCommits,
                                                RepoSyncStatusResponse analysisStatus,
                                                boolean hasSummary) {
        return new GithubRepositoryResponse(
                repo.getId(),
                repo.getGithubRepoId(),
                repo.getOwnerLogin(),
                repo.getRepoName(),
                repo.getFullName(),
                repo.getHtmlUrl(),
                repo.getVisibility().getValue(),
                repo.getDefaultBranch(),
                repo.isSelected(),
                hasCommits,
                analysisStatus,
                hasSummary,
                repo.getPushedAt(),
                repo.getOwnerType(),
                repo.getLanguage(),
                repo.getSecretExcludedFiles()
        );
    }
}
