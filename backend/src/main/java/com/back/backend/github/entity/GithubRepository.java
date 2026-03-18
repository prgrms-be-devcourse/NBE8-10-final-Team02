package com.back.backend.github.entity;

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

    @Column(name = "owner_login", nullable = false)
    private String ownerLogin;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Convert(converter = RepositoryVisibilityConverter.class)
    @Column(name = "visibility", nullable = false)
    private RepositoryVisibility visibility;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
