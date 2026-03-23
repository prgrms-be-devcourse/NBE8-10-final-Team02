package com.back.backend.domain.auth.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.domain.user.entity.User;
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
 * 소셜 로그인 인증 계정 정보를 저장하는 엔티티입니다.
 * <p>
 * 하나의 {@link User}(사람)는 여러 개의 {@code AuthAccount}(신분증)를 가질 수 있으며,
 * 이를 통해 구글, 깃허브 등 다중 소셜 연동을 지원합니다.
 */
@Getter
@Entity
@Builder
@Table(
        name = "auth_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_accounts_provider_user", columnNames = {"provider", "provider_user_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = AuthProviderConverter.class)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public void updateLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
