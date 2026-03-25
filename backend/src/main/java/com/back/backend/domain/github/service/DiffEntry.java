package com.back.backend.domain.github.service;

/**
 * git log -p에서 추출한 커밋 단위 diff.
 * RepoSummary 생성 시 LLM에 기여 증거로 제공한다.
 */
public record DiffEntry(
        String sha,       // 커밋 SHA (7자)
        String subject,   // 커밋 메시지 첫 줄
        String diff       // 해당 커밋의 diff 원문 (길이 제한 후 전달)
) {}
