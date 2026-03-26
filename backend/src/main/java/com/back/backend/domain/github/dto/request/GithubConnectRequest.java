package com.back.backend.domain.github.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /github/connections 요청 바디.
 *
 * mode는 "oauth"만 허용한다.
 * GitHub OAuth 완료 후 프론트에서 accessToken을 전달한다.
 * Google/Kakao 로그인 사용자는 GitHub 기능을 사용하려면 GitHub OAuth를 연동해야 한다.
 */
public record GithubConnectRequest(

        // 현재 "oauth"만 허용
        @NotBlank
        String mode,

        // 미사용 (하위 호환용 필드, url 모드 제거됨)
        String githubLogin,

        // GitHub OAuth 완료 후 프론트가 전달하는 access token.
        // 토큰은 로그에 남기지 않는다 (backend-conventions.md §12.3).
        String accessToken,

        // private repo 접근 동의 scope 문자열 (예: "read:user repo")
        String accessScope
) {}