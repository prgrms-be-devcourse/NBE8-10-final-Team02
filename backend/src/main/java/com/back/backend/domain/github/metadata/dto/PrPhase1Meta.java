package com.back.backend.domain.github.metadata.dto;

import java.time.Instant;
import java.util.List;

/**
 * GraphQL Phase 1 경량 조회 결과 — Impact Score 계산에 필요한 필드만 포함.
 * bodyText는 자동생성 패널티(bodyLen < 50 조건) 판별용으로만 사용한다.
 *
 * @param id                GraphQL 글로벌 노드 ID (Phase 2 nodes(ids:[...]) 조회에 사용)
 * @param number            PR 번호
 * @param title             PR 제목
 * @param bodyText          PR 본문 순수 텍스트 (bodyLen 계산용)
 * @param mergedAt          Merge 시각 (Time-bound early-exit 기준)
 * @param createdAt         생성 시각
 * @param additions         추가 라인 수
 * @param deletions         삭제 라인 수
 * @param totalCommentsCount 전체 댓글 수 (봇 포함 — 낮은 계수 ×2 적용)
 * @param authorLogin       작성자 GitHub 로그인명
 * @param authorTypename    작성자 타입: "User" | "Bot" | "EnterpriseUserAccount" | "Mannequin"
 * @param labels            PR 라벨명 목록 (최대 5개)
 * @param reviewTotalCount  공식 리뷰 수 (봇 리뷰가 드물어 신뢰도 높음 — 높은 계수 ×5 적용)
 * @param repoLabelCoverage repo 내 PR 중 라벨 사용 비율 (0.0~1.0)
 */
public record PrPhase1Meta(
        String id,
        int number,
        String title,
        String bodyText,
        Instant mergedAt,
        Instant createdAt,
        int additions,
        int deletions,
        int totalCommentsCount,
        String authorLogin,
        String authorTypename,
        List<String> labels,
        int reviewTotalCount,
        double repoLabelCoverage
) {}
