package com.back.backend.global.security.auth;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationRequiredException extends AuthenticationException {

    public AuthenticationRequiredException(String msg) {
        super(msg);
    }
}

