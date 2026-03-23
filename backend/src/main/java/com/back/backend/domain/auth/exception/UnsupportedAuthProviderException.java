package com.back.backend.domain.auth.exception;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class UnsupportedAuthProviderException extends ServiceException {

    public UnsupportedAuthProviderException(String provider) {
        super(
                ErrorCode.AUTH_UNSUPPORTED_PROVIDER,
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 로그인 제공자입니다."
        );
    }
}
