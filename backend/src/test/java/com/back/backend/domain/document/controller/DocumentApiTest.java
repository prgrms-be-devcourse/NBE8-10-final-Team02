package com.back.backend.document.controller;

import com.back.backend.document.dto.DocumentResponse;
import com.back.backend.document.service.DocumentService;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [API Test 계층]
// - ApiTestBase: SpringBootTest + Testcontainers + MockMvc 공통 설정 상속
// - @MockitoBean: Service 대체 → 비즈니스 로직이 아닌 HTTP 레이어에 집중
// - @WithMockUser: 인증된 사용자 시뮬레이션
class DocumentApiTest extends ApiTestBase {

    @MockitoBean
    private DocumentService documentService;

    // --- 인증/인가 ---

    @Test
    void uploadDocument_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    // --- request validation (422) ---

    @Test
    @WithMockUser
    void uploadDocument_returns422WhenInvalidMimeType() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_INVALID_TYPE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "지원하지 않는 파일 형식입니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[1024])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_INVALID_TYPE.name()));
    }

    @Test
    @WithMockUser
    void uploadDocument_returns422WhenFileTooLarge() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_FILE_TOO_LARGE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "파일 크기는 10MB를 초과할 수 없습니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[1024])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_FILE_TOO_LARGE.name()));
    }

    @Test
    @WithMockUser
    void uploadDocument_returns422WhenDocumentCountExceeded() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_UPLOAD_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "문서는 최대 5개까지 업로드할 수 있습니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_UPLOAD_FAILED.name()));
    }

    // --- 5xx ---

    @Test
    @WithMockUser
    void uploadDocument_returns500WhenStorageFails() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_UPLOAD_FAILED,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 저장에 실패했습니다.",
                true
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_UPLOAD_FAILED.name()));
    }

    // --- 정상 흐름 ---

    @Test
    @WithMockUser
    void uploadDocument_returns201WhenValid() throws Exception {
        DocumentResponse response = new DocumentResponse(
                1L, DocumentType.RESUME.getValue(), "resume.pdf",
                "application/pdf", 1024L,
                DocumentExtractStatus.PENDING.getValue(),
                Instant.parse("2026-01-01T00:00:00Z"), null, null
        );
        given(documentService.upload(any(), eq(DocumentType.OTHER), any())).willReturn(response);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.extractStatus").value("pending"))
                .andExpect(jsonPath("$.data.originalFileName").value("resume.pdf"));
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[1024]);
    }
}
