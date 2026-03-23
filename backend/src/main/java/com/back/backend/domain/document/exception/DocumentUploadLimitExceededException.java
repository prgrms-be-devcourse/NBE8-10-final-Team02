package com.back.backend.domain.document.exception;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class DocumentUploadLimitExceededException extends ServiceException {

    public DocumentUploadLimitExceededException() {
        super(
                ErrorCode.DOCUMENT_UPLOAD_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "문서는 최대 5개까지 업로드할 수 있습니다."
        );
    }
}
