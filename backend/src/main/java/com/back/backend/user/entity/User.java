package com.back.backend.user.entity;

import com.back.backend.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseTimeEntity {

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Convert(converter = UserStatusConverter.class)
    @Column(name = "status", nullable = false)
    private UserStatus status;
}
