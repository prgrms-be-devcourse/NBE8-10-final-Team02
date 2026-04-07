package com.back.backend.domain.github.metadata.dto;

import java.time.Instant;

/**
 * CHANGES_REQUESTED 리뷰 단건.
 *
 * APPROVED, COMMENTED를 제외하는 이유:
 *   - APPROVED: MERGED 상태 자체가 최종 승인을 표현하므로 중복.
 *   - COMMENTED: 단순 질문/칭찬은 자소서 소재로 활용 불가.
 *   - CHANGES_REQUESTED: "피드백 수용·개선" 스토리의 유일한 증거.
 *
 * 리뷰어별 최신 1개만 유지 (같은 리뷰어의 반복 CHANGES_REQUESTED 중 최신본).
 *
 * @param reviewerLogin 리뷰어 GitHub 로그인명
 * @param bodyExcerpt   정제된 리뷰 본문 (50자 미만이면 null — 전체 리뷰 제외)
 * @param submittedAt   리뷰 제출 시각 (dedup 기준)
 */
public record CollectedReview(
        String reviewerLogin,
        String bodyExcerpt,
        Instant submittedAt
) {}
