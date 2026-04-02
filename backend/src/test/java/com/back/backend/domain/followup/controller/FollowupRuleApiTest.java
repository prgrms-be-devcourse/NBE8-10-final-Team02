package com.back.backend.domain.followup.controller;

import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import com.back.backend.domain.followup.service.FollowupRuleService;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FollowupRuleApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @MockitoBean
    private FollowupRuleService followupRuleService;

    @Test
    void analyze_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/followup-rules/analyze")
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new FollowupAnalyzeRequest(
                                QuestionType.PROJECT,
                                "제가 맡아서 구현했습니다."
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analyze_returns200ForAliasPath() throws Exception {
        given(followupRuleService.analyze(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/followup-rules/analyze")
                        .with(authenticated(1L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new FollowupAnalyzeRequest(
                                QuestionType.PROJECT,
                                "제가 맡아서 구현했습니다."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionType").value("PROJECT"))
                .andExpect(jsonPath("$.data.signals.ROLE").value(true))
                .andExpect(jsonPath("$.data.primaryGap").value("RESULT"))
                .andExpect(jsonPath("$.data.secondaryGap").value("REASON"))
                .andExpect(jsonPath("$.data.finalAction").value("USE_CANDIDATE"))
                .andExpect(jsonPath("$.data.candidateQuestionTypes[0]").value("PROJECT_RESULT_DETAIL"));
    }

    @Test
    void analyze_returns200ForVersionedPath() throws Exception {
        given(followupRuleService.analyze(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/followup-rules/analyze")
                        .with(authenticated(1L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new FollowupAnalyzeRequest(
                                QuestionType.PROJECT,
                                "제가 맡아서 구현했습니다."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionType").value("PROJECT"))
                .andExpect(jsonPath("$.data.finalAction").value("USE_CANDIDATE"));
    }

    @Test
    void analyze_returns400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/followup-rules/analyze")
                        .with(authenticated(1L))
                        .contentType("application/json")
                        .content("""
                                {
                                  "questionType": "PROJECT",
                                  "answerText": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("answerText"));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private FollowupAnalyzeResponse sampleResponse() {
        return new FollowupAnalyzeResponse(
                QuestionType.PROJECT,
                Map.of(
                        GapType.ROLE, true,
                        GapType.ACTION, true,
                        GapType.RESULT, false,
                        GapType.REASON, false
                ),
                List.of(GapType.RESULT, GapType.REASON),
                GapType.RESULT,
                GapType.REASON,
                FinalAction.USE_CANDIDATE,
                List.of(
                        CandidateQuestionType.PROJECT_RESULT_DETAIL,
                        CandidateQuestionType.PROJECT_APPROACH_REASON
                )
        );
    }
}
