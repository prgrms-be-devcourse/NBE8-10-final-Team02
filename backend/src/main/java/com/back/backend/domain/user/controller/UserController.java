package com.back.backend.domain.user.controller;

import com.back.backend.domain.user.dto.response.UserProfileResponse;
import com.back.backend.domain.user.service.UserService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMe(Authentication authentication) {
        long userId = currentUserResolver.resolve(authentication).id();
        return ApiResponse.success(userService.getMyProfile(userId));
    }
}
