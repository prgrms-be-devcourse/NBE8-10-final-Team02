package com.back.backend.github.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
        name = "github_connections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_connections_user", columnNames = {"user_id"}),
                @UniqueConstraint(name = "uk_github_connections_github_user", columnNames = {"github_user_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GithubConnection extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_user_id", nullable = false)
    private Long githubUserId;

    @Column(name = "github_login", nullable = false, length = 255)
    private String githubLogin;

    @Column(name = "access_scope", columnDefinition = "text")
    private String accessScope;

    @Convert(converter = GithubSyncStatusConverter.class)
    @Column(name = "sync_status", nullable = false, length = 20)
    private GithubSyncStatus syncStatus;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
