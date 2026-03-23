package com.back.backend.domain.document.exception;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class DocumentInvalidTypeException extends ServiceException {

    public DocumentInvalidTypeException() {
        super(
                ErrorCode.DOCUMENT_INVALID_TYPE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "지원하지 않는 파일 형식입니다."
        );
    }
}
