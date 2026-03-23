package com.back.backend.domain.user.dto.response;

import com.back.backend.domain.user.entity.User;

public record UserProfileResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        String status
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getStatus().getValue()
        );
    }
}
