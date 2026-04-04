package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesConfig;
import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class FollowupHoldoutRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FollowupRuleService followupRuleService;

    @BeforeEach
    void setUp() {
        FollowupRulesProperties properties = FollowupRulesConfig.loadRules(
                new ClassPathResource("followup/rules/followup-rules-v0.2.yaml")
        );

        followupRuleService = new FollowupRuleService(
                new TextNormalizer(),
                new SignalExtractor(properties),
                new GapResolver(properties),
                new FinalActionDecider(properties),
                new CandidateQuestionSelector(properties)
        );
    }

    @TestFactory
    Stream<DynamicTest> analyze_curatedHoldoutCases() {
        return loadHoldoutCases().stream()
                .map(holdoutCase -> dynamicTest(holdoutCase.caseId(), () -> assertHoldoutCase(holdoutCase)));
    }

    private void assertHoldoutCase(HoldoutCase holdoutCase) {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                holdoutCase.questionType(),
                holdoutCase.answerText()
        ));

        for (GapType gapType : holdoutCase.expectedPresentSignals()) {
            assertThat(response.signals()).containsEntry(gapType, true);
        }

        for (GapType gapType : holdoutCase.expectedAbsentSignals()) {
            assertThat(response.signals()).containsEntry(gapType, false);
        }

        assertThat(response.finalAction()).isEqualTo(holdoutCase.expectedFinalAction());
        assertThat(response.primaryGap()).isEqualTo(holdoutCase.expectedPrimaryGap());
        assertThat(response.secondaryGap()).isEqualTo(holdoutCase.expectedSecondaryGap());
        assertThat(response.candidateQuestionTypes()).containsExactlyElementsOf(
                holdoutCase.expectedCandidateQuestionTypes()
        );
    }

    private List<HoldoutCase> loadHoldoutCases() {
        ClassPathResource resource = new ClassPathResource("followup/holdout/curated-holdout-v1.json");

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record HoldoutCase(
            String caseId,
            QuestionType questionType,
            String answerText,
            FinalAction expectedFinalAction,
            GapType expectedPrimaryGap,
            GapType expectedSecondaryGap,
            List<CandidateQuestionType> expectedCandidateQuestionTypes,
            List<GapType> expectedPresentSignals,
            List<GapType> expectedAbsentSignals
    ) {
    }
}
