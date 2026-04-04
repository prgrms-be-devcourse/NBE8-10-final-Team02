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
    void extract_ignoresExampleLogCountForMetricSignal() {
        String normalizedAnswerText = textNormalizer.normalize(
                "예시 로그 12건을 한 표로 정리해 서로 다르게 계산된 지점을 설명했습니다."
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
    void extract_recognizesSetupTimeAsResultSignal() {
        String normalizedAnswerText = textNormalizer.normalize(
                "새 팀원이 환경을 맞추는 시간이 확실히 줄었습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
    }

    @Test
    void extract_recognizesConcessiveResponseTimeStabilizationAsResultSignal() {
        String normalizedAnswerText = textNormalizer.normalize(
                "응답 시간도 다시 안정화됐지만, 이후 비슷한 이슈를 먼저 막는 장치까지는 아직 넣지 못했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
    }

    @Test
    void extract_recognizesReasonWhenTechChoiceIsSimplerToOperateInOnePlace() {
        String normalizedAnswerText = textNormalizer.normalize(
                "실행 이력과 실패 재처리를 한 화면에서 보려면 그쪽이 더 단순하다고 봤습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
    }

    @Test
    void extract_recognizesReasonWhenLocalStartupIsMoreImportant() {
        String normalizedAnswerText = textNormalizer.normalize(
                "팀 규모가 작아서 로컬을 바로 띄우는 편이 더 중요했고, 운영 환경만 별도 파이프라인으로 분리했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
    }

    @Test
    void extract_recognizesTradeoffWhenDualOperationalSpecBurdenRemains() {
        String normalizedAnswerText = textNormalizer.normalize(
                "대신 운영 스펙을 두 벌로 맞춰야 하는 부담은 남았지만, 새 팀원이 환경을 맞추는 시간은 줄었습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.TRADEOFF)).isNotEmpty();
    }

    @Test
    void extract_recognizesViewpointDifferenceAsIssueSignalWhenDebateRepeats() {
        String normalizedAnswerText = textNormalizer.normalize(
                "qa팀과 서비스팀의 관점 차이가 있어서 논쟁이 반복됐습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
    }

    @Test
    void extract_recognizesViewpointDifferenceAsIssueSignalWhenPerspectivesKeepDiverging() {
        String normalizedAnswerText = textNormalizer.normalize(
                "고객사 운영팀과 우리 팀의 관점 차이가 계속 어긋나서 논의가 길어졌습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
    }

    @Test
    void extract_recognizesDefinitionDriftAsIssueSignalWhenDebateGetsLonger() {
        String normalizedAnswerText = textNormalizer.normalize(
                "정산 규칙을 맞출 때 데이터팀과 해석 기준이 계속 달라 논의가 길어졌습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
    }

    @Test
    void extract_recognizesRepeatedDefinitionDriftAsIssueSignal() {
        String normalizedAnswerText = textNormalizer.normalize(
                "qa팀과 서비스팀의 해석 기준이 번번이 달라 논의가 길어졌습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
    }

    @Test
    void extract_recognizesReasonWhenSharedEvidenceIsReviewedTogether() {
        String normalizedAnswerText = textNormalizer.normalize(
                "실제 조회 빈도와 장애 대응 사례를 같이 본 덕분에 왜 그 기준으로 가는지 모두가 납득했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
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

    @Test
    void extract_recognizesActionWhenLockKeyCombinationIsSplitAgain() {
        String normalizedAnswerText = textNormalizer.normalize(
                "저는 락 키 조합을 다시 나눴고 배포 뒤에는 운영 로그와 대시보드를 다시 확인했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ACTION)).isNotEmpty();
    }
}
