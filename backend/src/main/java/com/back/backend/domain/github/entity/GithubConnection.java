package com.back.backend.domain.github.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.domain.user.entity.User;
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

    // GitHub API 호출에 사용할 OAuth access token.
    // 운영 환경에서는 암호화 저장을 권장한다 (backend-conventions.md §12.3).
    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    /**
     * 연결 정보를 갱신한다 (재연동, token 재발급 등).
     */
    public void update(Long githubUserId, String githubLogin, String accessToken, String accessScope, Instant connectedAt) {
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.accessToken = accessToken;
        this.accessScope = accessScope;
        this.connectedAt = connectedAt;
        this.syncStatus = GithubSyncStatus.PENDING;
    }

    /**
     * 동기화 성공 처리. last_synced_at을 갱신하고 status를 SUCCESS로 바꾼다.
     */
    public void markSyncSuccess(Instant syncedAt) {
        this.syncStatus = GithubSyncStatus.SUCCESS;
        this.lastSyncedAt = syncedAt;
    }

    /**
     * 동기화 실패 처리. status를 FAILED로 바꾼다.
     * 실패도 상태 컬럼에 남겨 원인 추적이 가능하게 한다 (backend-conventions.md §9.3).
     */
    public void markSyncFailed() {
        this.syncStatus = GithubSyncStatus.FAILED;
    }
}
