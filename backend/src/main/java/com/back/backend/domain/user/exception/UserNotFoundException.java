package com.back.backend.domain.user.exception;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ServiceException {

    public UserNotFoundException(Long userId) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "사용자를 찾을 수 없습니다."
        );
    }
}
