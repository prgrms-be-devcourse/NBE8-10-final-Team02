package com.back.backend.domain.user.controller;

import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.domain.auth.repository.AuthAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;

    public UserController(UserRepository userRepository, AuthAccountRepository authAccountRepository) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
    }

    /**
     * 현재 로그인 사용자 정보 반환.
     * GET /api/v1/users/me
     *
     * connectedProviders: 이 사용자가 연결한 소셜 로그인 provider 목록.
     * 예: ["github"], ["google"], ["kakao", "github"]
     * 프론트엔드에서 GitHub 연동 여부를 판단해 적절한 UI를 보여주는 데 사용한다.
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getMe(Authentication authentication) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;
        long userId = (Long) token.getPrincipal();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // 이 사용자에 연결된 소셜 provider 목록 (소문자, e.g. "github", "google", "kakao")
        List<String> providers = authAccountRepository.findByUser(user).stream()
                .map(a -> a.getProvider().name().toLowerCase())
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("displayName", user.getDisplayName());
        data.put("email", user.getEmail() != null ? user.getEmail() : "");
        data.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
        data.put("status", user.getStatus().getValue());
        data.put("connectedProviders", providers);

        return ApiResponse.success(data);
    }
}
