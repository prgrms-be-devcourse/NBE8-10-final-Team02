package com.back.backend.global.security.auth;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationInvalidTokenException extends AuthenticationException {

    public AuthenticationInvalidTokenException(String msg) {
        super(msg);
    }

    public AuthenticationInvalidTokenException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

