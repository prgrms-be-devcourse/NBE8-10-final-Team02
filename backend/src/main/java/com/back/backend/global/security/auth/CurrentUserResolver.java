package com.back.backend.global.security.auth;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUserResolver {

    public CurrentUser resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ServiceException(
                    ErrorCode.AUTH_REQUIRED,
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Long userId = extractUserId(authentication.getPrincipal());
        if (userId == null) {
            throw new ServiceException(
                    ErrorCode.AUTH_INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 인증 정보입니다."
            );
        }

        return new CurrentUser(userId);
    }

    private Long extractUserId(Object principal) {
        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof Number number) {
            return number.longValue();
        }

        if (principal instanceof String value && StringUtils.hasText(value) && value.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(value);
        }

        return null;
    }
}
