package com.back.backend.domain.github.metadata.dto;

import java.time.Instant;

/**
 * GraphQL Phase 2 상세 조회 후 정제된 Issue 단건.
 *
 * @param number      Issue 번호
 * @param title       제목
 * @param bodyExcerpt 정제된 본문 발췌 (30자 미만이면 null — title만 AI에 주입)
 * @param closedAt    닫힌 시각 (COMPLETED 기준)
 * @param createdAt   생성 시각
 */
public record CollectedIssue(
        int number,
        String title,
        String bodyExcerpt,
        Instant closedAt,
        Instant createdAt
) {}
