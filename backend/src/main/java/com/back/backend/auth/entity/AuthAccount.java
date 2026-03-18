package com.back.backend.auth.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.user.entity.User;
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
    @Column(name = "provider", nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
