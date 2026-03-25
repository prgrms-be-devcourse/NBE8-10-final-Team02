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

@Getter
@Entity
@Builder
@Table(
        name = "github_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_repositories_connection_repo", columnNames = {"github_connection_id", "github_repo_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GithubRepository extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_connection_id", nullable = false)
    private GithubConnection githubConnection;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "owner_login", nullable = false, length = 255)
    private String ownerLogin;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "html_url", nullable = false, length = 1000)
    private String htmlUrl;

    @Convert(converter = RepositoryVisibilityConverter.class)
    @Column(name = "visibility", nullable = false, length = 20)
    private RepositoryVisibility visibility;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "repo_size_kb")
    private Integer repoSizeKb;

    /**
     * GitHub API에서 새로 받아온 값으로 repo 정보를 갱신한다.
     * visibility, defaultBranch, htmlUrl은 바뀔 수 있어 매 동기화마다 덮어쓴다.
     */
    public void sync(RepositoryVisibility visibility, String defaultBranch, String htmlUrl, Instant syncedAt) {
        this.visibility = visibility;
        this.defaultBranch = defaultBranch;
        this.htmlUrl = htmlUrl;
        this.syncedAt = syncedAt;
    }

    /**
     * 사용자가 선택/해제한 상태를 저장한다.
     * is_selected = true 인 repo만 커밋 동기화 대상이 된다.
     */
    public void updateSelection(boolean selected) {
        this.selected = selected;
    }
}
