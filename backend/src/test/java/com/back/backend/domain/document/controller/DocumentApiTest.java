package com.back.backend.domain.document.controller;

import com.back.backend.domain.document.service.DocumentService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
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

    @Test
    void uploadDocument_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    @WithMockUser
    void uploadDocument_returns422WhenInvalidMimeType() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_INVALID_TYPE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "지원하지 않는 파일 형식입니다."
        )).given(documentService).validateUpload(any(), eq("image/png"), anyLong());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[1024])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DOCUMENT_INVALID_TYPE.name()));
    }

    @Test
    @WithMockUser
    void uploadDocument_returns201WhenValid() throws Exception {
        willDoNothing().given(documentService).validateUpload(any(), any(), anyLong());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(pdfFile("resume.pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[1024]);
    }
}
