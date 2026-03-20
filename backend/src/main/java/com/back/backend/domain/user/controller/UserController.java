package com.back.backend.domain.user.controller;

import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 현재 로그인 사용자 정보 반환.
     * GET /api/v1/users/me
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getMe(Authentication authentication) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;
        long userId = (Long) token.getPrincipal();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                "status", user.getStatus().getValue()
        ));
    }
}
