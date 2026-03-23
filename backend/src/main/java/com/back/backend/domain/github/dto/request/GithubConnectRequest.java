package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /github/connections 요청 바디.
 *
 * mode 별 필수 필드:
 *   - oauth: accessToken 필수. GitHub OAuth 완료 후 프론트에서 전달.
 *   - url:   githubLogin 필수. public repo만 접근 가능.
 *
 * NOTE: openapi.yaml에 accessToken 필드가 누락되어 있다. 문서 동기화 필요.
 */
public record GithubConnectRequest(

        // "oauth" 또는 "url"
        @NotBlank
        String mode,

        // url 모드에서 사용자가 직접 입력한 GitHub login (예: "octocat")
        String githubLogin,

        // oauth 모드에서 프론트가 GitHub OAuth 완료 후 전달하는 access token.
        // 토큰은 로그에 남기지 않는다 (backend-conventions.md §12.3).
        String accessToken,

        // private repo 접근 동의 scope 문자열 (예: "read:user repo")
        String accessScope
) {}