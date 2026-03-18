package com.back.backend.global;

import com.back.backend.TestcontainersConfiguration;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.request.RequestIdContext;
import com.back.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ApiContractTest.ContractTestController.class})
class ApiContractTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void successResponseIncludesRequestIdAndMeta() throws Exception {
        mockMvc.perform(get("/contract-test/success")
                        .header(RequestIdContext.HEADER_NAME, "req_manual_contract"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdContext.HEADER_NAME, "req_manual_contract"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.meta.requestId").value("req_manual_contract"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void validationFailureUsesCommonErrorContract() throws Exception {
        mockMvc.perform(post("/contract-test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("required"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void serviceExceptionUsesCommonErrorContract() throws Exception {
        mockMvc.perform(get("/contract-test/service-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()))
                .andExpect(jsonPath("$.error.message").value("리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void protectedEndpointReturnsAuthRequiredError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdContext.HEADER_NAME))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void directBusinessPathAlsoReturnsAuthRequiredError() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdContext.HEADER_NAME))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @RestController
    @RequestMapping("/contract-test")
    static class ContractTestController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("status", "ok"));
        }

        @PostMapping("/validation")
        ApiResponse<Map<String, String>> validation(@Valid @RequestBody ValidationRequest request) {
            return ApiResponse.success(Map.of("name", request.name()));
        }

        @GetMapping("/service-error")
        ApiResponse<Void> serviceError() {
            throw new ServiceException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "리소스를 찾을 수 없습니다."
            );
        }
    }

    record ValidationRequest(
            @NotBlank(message = "required")
            String name
    ) {
    }
}
