package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesConfig;
import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.GapType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalExtractorTest {

    private TextNormalizer textNormalizer;
    private SignalExtractor signalExtractor;

    @BeforeEach
    void setUp() {
        FollowupRulesProperties properties = FollowupRulesConfig.loadRules(
                new ClassPathResource("followup/rules/followup-rules-v0.2.yaml")
        );
        textNormalizer = new TextNormalizer();
        signalExtractor = new SignalExtractor(properties);
    }

    @Test
    void extract_ignoresSampleDataCountForMetricSignal() {
        String normalizedAnswerText = textNormalizer.normalize(
                "샘플 데이터 20건을 뽑아 계산식과 실제 결과를 비교하는 검증 문서를 만들었습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.METRIC)).isEmpty();
    }

    @Test
    void extract_keepsOperationalCountMetricWhenCountRepresentsOutcome() {
        String normalizedAnswerText = textNormalizer.normalize(
                "배포 후 문의가 하루 30건에서 5건 수준으로 줄었습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.METRIC)).isNotEmpty();
    }

    @Test
    void extract_treatsRegressionTestCheckAsVerificationWithoutPrevention() {
        String normalizedAnswerText = textNormalizer.normalize(
                "수정 후 회귀 테스트로 같은 요청 시나리오를 다시 확인했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.VERIFICATION)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.PREVENTION)).isEmpty();
    }
}
