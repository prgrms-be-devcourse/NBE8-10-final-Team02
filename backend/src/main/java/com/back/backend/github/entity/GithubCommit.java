package com.back.backend.github.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
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
        name = "github_commits",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_commits_repository_sha", columnNames = {"repository_id", "github_commit_sha"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GithubCommit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private GithubRepository repository;

    @Column(name = "github_commit_sha", nullable = false, length = 64)
    private String githubCommitSha;

    @Column(name = "author_login", length = 255)
    private String authorLogin;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "author_email", length = 255)
    private String authorEmail;

    @Column(name = "commit_message", nullable = false, columnDefinition = "text")
    private String commitMessage;

    @Column(name = "is_user_commit", nullable = false)
    private boolean userCommit;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;
}
