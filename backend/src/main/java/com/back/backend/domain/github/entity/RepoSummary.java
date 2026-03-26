package com.back.backend.domain.github.entity;

import com.back.backend.domain.user.entity.User;
import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * repo 단위 AI 분석 요약본.
 *
 * data 필드: portfolio-summary.schema.json 형식의 JSON (projects 배열 1개 항목 포함).
 * 버전이 쌓이면 summary_version이 증가한다. 최신 버전만 면접/자소서에 사용.
 */
@Getter
@Entity
@Builder
@Table(name = "repo_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RepoSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_repository_id", nullable = false)
    private GithubRepository githubRepository;

    @Column(name = "summary_version", nullable = false)
    private int summaryVersion;

    /** portfolio-summary.schema.json 형식의 JSON (projects 1개 항목) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "trigger_reason", nullable = false, length = 30)
    private String triggerReason;

    @Column(name = "last_synced_commit", length = 64)
    private String lastSyncedCommit;

    @Column(name = "significance_score")
    private Integer significanceScore;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
