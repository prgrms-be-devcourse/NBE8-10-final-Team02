package com.back.backend.domain.github.metadata.dto;

import java.time.Instant;
import java.util.List;

/**
 * GraphQL Phase 2 상세 조회 후 정제된 PR 단건.
 *
 * @param number      PR 번호
 * @param title       PR 제목
 * @param bodyExcerpt 정제된 본문 발췌 (50자 미만이면 null — title만 AI에 주입)
 * @param mergedAt    Merge 시각
 * @param createdAt   생성 시각
 * @param reviews     CHANGES_REQUESTED 리뷰 목록 (리뷰어별 최신 1개)
 */
public record CollectedPullRequest(
        int number,
        String title,
        String bodyExcerpt,
        Instant mergedAt,
        Instant createdAt,
        List<CollectedReview> reviews
) {}
