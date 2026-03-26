package com.back.backend.global.security.auth;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw authRequired();
        }

        // JWT 인증과 테스트용 @WithMockUser principal을 모두 같은 방식으로 흡수한다.
        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof Number userIdNumber) {
            return userIdNumber.longValue();
        }
        if (principal instanceof UserDetails userDetails) {
            return parseUserId(userDetails.getUsername());
        }
        if (principal instanceof String principalText && !"anonymousUser".equals(principalText)) {
            return parseUserId(principalText);
        }

        throw authRequired();
    }

    private long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw authRequired();
        }
    }

    private ServiceException authRequired() {
        return new ServiceException(
                ErrorCode.AUTH_REQUIRED,
                HttpStatus.UNAUTHORIZED,
                "로그인이 필요합니다."
        );
    }
}
