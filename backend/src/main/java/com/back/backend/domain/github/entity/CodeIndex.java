package com.back.backend.domain.github.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 정적 분석 결과 (FQN → 파일 위치 매핑).
 *
 * 소스 원문은 OCI 파일시스템의 clone에서 직접 읽으므로 DB에는 매핑 정보만 저장한다.
 * 소스 읽기: CodeIndexService.getClassSource() 참조.
 */
@Getter
@Entity
@Builder
@Table(
        name = "code_index",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_code_index_repo_fqn",
                        columnNames = {"github_repository_id", "fqn"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CodeIndex extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_repository_id", nullable = false)
    private GithubRepository githubRepository;

    @Column(name = "fqn", nullable = false, length = 1000)
    private String fqn;

    @Column(name = "file_path", nullable = false, length = 2000)
    private String filePath;

    @Column(name = "loc_start")
    private Integer locStart;

    @Column(name = "loc_end")
    private Integer locEnd;

    @Convert(converter = NodeTypeConverter.class)
    @Column(name = "node_type", nullable = false, length = 20)
    private NodeType nodeType;

    @Column(name = "pagerank")
    private Double pagerank;

    @Column(name = "authored_by_me", nullable = false)
    private boolean authoredByMe;

    /**
     * JSON 배열 형태로 메서드 목록 저장.
     * 예: [{"name":"doFilter","signature":"doFilter(...)","locStart":20,"locEnd":50}]
     */
    @Column(name = "methods", columnDefinition = "jsonb")
    private String methods;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    /**
     * pagerank와 authoredByMe는 분석 완료 후 갱신된다.
     */
    public void updatePagerank(double pagerank) {
        this.pagerank = pagerank;
    }
}
