package com.back.backend.domain.github.entity;

import com.back.backend.domain.user.entity.User;
import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자의 전체 repo를 통합한 포트폴리오 요약본.
 *
 * data 필드: portfolio-summary.schema.json 형식 JSON (projects N개 + globalStrengths + leanSummary).
 * merge_strategy: json_aggregate(LLM 없이 집계) | llm_rewrite(LLM 재생성).
 */
@Getter
@Entity
@Builder
@Table(name = "merged_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MergedSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "merged_version", nullable = false)
    private int mergedVersion;

    /** JSON 배열 형태 repo ID 목록. 예: ["123", "456"] */
    @Column(name = "included_repo_ids", nullable = false, columnDefinition = "jsonb")
    private String includedRepoIds;

    /**
     * portfolio-summary.schema.json 형식 JSON + leanSummary 확장 필드.
     * AI 출력(projects, globalStrengths, globalRisks, qualityFlags) + 파생(leanSummary).
     */
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "merge_strategy", nullable = false, length = 20)
    private String mergeStrategy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
