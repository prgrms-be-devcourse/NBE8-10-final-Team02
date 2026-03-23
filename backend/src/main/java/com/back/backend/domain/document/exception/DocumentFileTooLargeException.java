package com.back.backend.domain.document.exception;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class DocumentFileTooLargeException extends ServiceException {

    public DocumentFileTooLargeException() {
        super(
                ErrorCode.DOCUMENT_FILE_TOO_LARGE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "파일 크기는 10MB를 초과할 수 없습니다."
        );
    }
}
