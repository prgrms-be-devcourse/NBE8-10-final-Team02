package com.back.backend.global.security.auth;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationExpiredTokenException extends AuthenticationException {

    public AuthenticationExpiredTokenException(String msg) {
        super(msg);
    }

    public AuthenticationExpiredTokenException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

