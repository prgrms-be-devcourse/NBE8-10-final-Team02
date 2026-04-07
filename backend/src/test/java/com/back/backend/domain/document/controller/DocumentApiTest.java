package com.back.backend.domain.document.controller;

import com.back.backend.domain.document.dto.DocumentResponse;
import com.back.backend.domain.document.service.DocumentService;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Document API의 HTTP 레이어를 검증하는 통합 테스트.
 *
 * <p><b>테스트 전략</b></p>
 * <ul>
 *   <li>{@code ApiTestBase}: SpringBootTest + Testcontainers + MockMvc 공통 설정을 상속</li>
 *   <li>{@code @MockitoBean}: {@link DocumentService}를 mock으로 대체해 HTTP 레이어에만 집중</li>
 *   <li>{@code authenticated(userId)}: {@link JwtAuthenticationToken}을 직접 주입해
 *       {@code @AuthenticationPrincipal Long userId}가 실제 값으로 바인딩되도록 한다</li>
 * </ul>
 *
 * <p><b>검증 범위</b>: 인증(401) → 요청 유효성(422) → 서버 오류(500) → 정상 흐름(2xx)</p>
 */
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
    void uploadDocument_returns422WhenInvalidMimeType() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_INVALID_TYPE,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "지원하지 않는 파일 형식입니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[1024]))
                .with(authenticated(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_INVALID_TYPE.name()));
    }

    @Test
    void uploadDocument_returns422WhenFileTooLarge() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_FILE_TOO_LARGE,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "파일 크기는 10MB를 초과할 수 없습니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[1024]))
                .with(authenticated(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_FILE_TOO_LARGE.name()));
    }

    @Test
    void uploadDocument_returns422WhenDocumentCountExceeded() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_UPLOAD_FAILED,
            HttpStatus.UNPROCESSABLE_CONTENT,
            "문서는 최대 5개까지 업로드할 수 있습니다."
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                .file(pdfFile("resume.pdf"))
                .with(authenticated(1L)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_UPLOAD_FAILED.name()));
    }

    // --- 5xx ---

    @Test
    void uploadDocument_returns500WhenStorageFails() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_UPLOAD_FAILED,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "파일 저장에 실패했습니다.",
            true
        )).given(documentService).upload(any(), any(), any());

        mockMvc.perform(multipart("/api/v1/documents")
                .file(pdfFile("resume.pdf"))
                .with(authenticated(1L)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_UPLOAD_FAILED.name()));
    }

    // --- 정상 흐름 ---

    @Test
    void uploadDocument_returns201WhenValid() throws Exception {
        DocumentResponse response = new DocumentResponse(
            1L, DocumentType.RESUME.getValue(), "resume.pdf",
            "application/pdf", 1024L,
            DocumentExtractStatus.PENDING.getValue(),
            Instant.parse("2026-01-01T00:00:00Z"), null, null
        );
        given(documentService.upload(any(), eq(DocumentType.OTHER), any())).willReturn(response);

        mockMvc.perform(multipart("/api/v1/documents")
                .file(pdfFile("resume.pdf"))
                .with(authenticated(1L)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.data.extractStatus").value("pending"))
            .andExpect(jsonPath("$.data.originalFileName").value("resume.pdf"));
    }

    // =========================================================
    // GET /api/v1/documents
    // =========================================================

    @Test
    void getDocuments_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void getDocuments_returns200WithList() throws Exception {
        DocumentResponse doc1 = new DocumentResponse(
            1L, DocumentType.RESUME.getValue(), "resume.pdf",
            "application/pdf", 1024L,
            DocumentExtractStatus.SUCCESS.getValue(),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:02Z"),
            "extracted text"
        );
        DocumentResponse doc2 = new DocumentResponse(
            2L, DocumentType.AWARD.getValue(), "award.pdf",
            "application/pdf", 2048L,
            DocumentExtractStatus.PENDING.getValue(),
            Instant.parse("2026-01-02T00:00:00Z"), null, null
        );
        given(documentService.getDocuments(any())).willReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/v1/documents").with(authenticated(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].documentType").value("resume"))
            .andExpect(jsonPath("$.data[1].id").value(2))
            .andExpect(jsonPath("$.data[1].documentType").value("award"));
    }

    @Test
    void getDocuments_returns200WithEmptyList() throws Exception {
        given(documentService.getDocuments(any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/documents").with(authenticated(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    // =========================================================
    // GET /api/v1/documents/{documentId}
    // =========================================================

    @Test
    void getDocument_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/documents/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void getDocument_returns200WhenFound() throws Exception {
        DocumentResponse doc = new DocumentResponse(
            1L, DocumentType.RESUME.getValue(), "resume.pdf",
            "application/pdf", 1024L,
            DocumentExtractStatus.SUCCESS.getValue(),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:02Z"),
            "extracted text"
        );
        given(documentService.getDocument(any(), eq(1L))).willReturn(doc);

        mockMvc.perform(get("/api/v1/documents/1").with(authenticated(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.originalFileName").value("resume.pdf"))
            .andExpect(jsonPath("$.data.extractStatus").value("success"))
            .andExpect(jsonPath("$.data.extractedText").value("extracted text"));
    }

    @Test
    void getDocument_returns404WhenNotFound() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "문서를 찾을 수 없습니다."
        )).given(documentService).getDocument(any(), eq(99L));

        mockMvc.perform(get("/api/v1/documents/99").with(authenticated(1L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_NOT_FOUND.name()));
    }

    // =========================================================
    // DELETE /api/v1/documents/{documentId}
    // =========================================================

    @Test
    void deleteDocument_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void deleteDocument_returns204WhenDeleted() throws Exception {
        willDoNothing().given(documentService).deleteDocument(any(), eq(1L));

        mockMvc.perform(delete("/api/v1/documents/1").with(authenticated(1L)))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteDocument_returns404WhenNotFound() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "문서를 찾을 수 없습니다."
        )).given(documentService).deleteDocument(any(), eq(99L));

        mockMvc.perform(delete("/api/v1/documents/99").with(authenticated(1L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_NOT_FOUND.name()));
    }

    @Test
    void deleteDocument_returns409WhenDocumentInUse() throws Exception {
        willThrow(new ServiceException(
            ErrorCode.DOCUMENT_IN_USE,
            HttpStatus.CONFLICT,
            "지원 단위에서 참조 중인 문서는 삭제할 수 없습니다."
        )).given(documentService).deleteDocument(any(), eq(1L));

        mockMvc.perform(delete("/api/v1/documents/1").with(authenticated(1L)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_IN_USE.name()));
    }

    // =========================================================
    // GET /api/v1/documents/search
    // =========================================================

    @Test
    void searchDocuments_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/documents/search").param("q", "Spring Boot"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void searchDocuments_returns200WithMatchingResults() throws Exception {
        DocumentResponse doc = new DocumentResponse(
            1L, DocumentType.RESUME.getValue(), "이력서.pdf",
            "application/pdf", 2048L,
            DocumentExtractStatus.SUCCESS.getValue(),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:02Z"),
            "Spring Boot 백엔드 개발자"
        );
        given(documentService.search(eq(1L), eq("Spring Boot"))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/documents/search")
                .param("q", "Spring Boot")
                .with(authenticated(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].originalFileName").value("이력서.pdf"));
    }

    @Test
    void searchDocuments_returns200WithEmptyResultWhenNoMatch() throws Exception {
        given(documentService.search(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/documents/search")
                .param("q", "Python")
                .with(authenticated(1L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    // =========================================================
    // Test helpers
    // =========================================================

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[1024]);
    }

    /** JwtAuthenticationToken을 주입해 @AuthenticationPrincipal Long userId가 실제 값으로 바인딩되도록 한다. */
    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}