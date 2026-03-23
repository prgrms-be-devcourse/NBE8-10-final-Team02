package com.back.backend.domain.document.controller;

import com.back.backend.domain.document.dto.response.DocumentResponse;
import com.back.backend.domain.document.service.DocumentService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentApiTest extends ApiTestBase {

    @MockitoBean
    private DocumentService documentService;

    @Test
    void uploadDocument_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .param("documentType", "resume")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void uploadDocument_returns422WhenInvalidMimeType() throws Exception {
        given(documentService.uploadDocument(anyLong(), any())).willThrow(new ServiceException(
                ErrorCode.DOCUMENT_INVALID_TYPE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "지원하지 않는 파일 형식입니다."
        ));

        mockMvc.perform(multipart("/api/v1/documents")
                        .param("documentType", "resume")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[1024]))
                        .with(authentication(new JwtAuthenticationToken(1L, List.of()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_INVALID_TYPE.name()));
    }

    @Test
    void uploadDocument_returns201WhenValid() throws Exception {
        given(documentService.uploadDocument(anyLong(), any())).willReturn(new DocumentResponse(
                301L,
                "resume",
                "resume.pdf",
                "application/pdf",
                1024L,
                "pending",
                Instant.parse("2026-03-17T10:31:10Z"),
                null,
                null
        ));

        mockMvc.perform(multipart("/api/v1/documents")
                        .param("documentType", "resume")
                        .file(pdfFile("resume.pdf"))
                        .with(authentication(new JwtAuthenticationToken(1L, List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(301))
                .andExpect(jsonPath("$.data.documentType").value("resume"))
                .andExpect(jsonPath("$.data.originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSizeBytes").value(1024))
                .andExpect(jsonPath("$.data.extractStatus").value("pending"));
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[1024]);
    }
}
