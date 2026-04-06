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

    @Test
    void extract_recognizesActionWhenQueryAndBatchFlowAreSplitNaturally() {
        String normalizedAnswerText = textNormalizer.normalize(
                "저는 서버 담당으로 조회 쿼리와 배치 흐름을 나눴습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ACTION)).isNotEmpty();
    }

    @Test
    void extract_recognizesAlternativeAndTradeoffForSingleApplicationMonolithWording() {
        String normalizedAnswerText = textNormalizer.normalize(
                "초기 관리자 도구는 마이크로서비스까지 쪼개지 않고 한 애플리케이션으로 갔습니다. "
                        + "나중에 경계가 커지면 나눠야 하는 부담은 남았습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ALTERNATIVE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.TRADEOFF)).isNotEmpty();
    }

    @Test
    void extract_recognizesQueueToolChoiceAsAlternativeReasonAndTradeoff() {
        String normalizedAnswerText = textNormalizer.normalize(
                "이벤트 적재는 Kafka로 바로 가지 않고 한동안 RabbitMQ로 운영했습니다. "
                        + "팀에서 이미 보고 있던 도구라 장애가 나면 큐 상태를 바로 확인하기 쉬웠습니다. "
                        + "대량 재처리는 불편할 수 있다는 부담은 있었습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ALTERNATIVE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.TRADEOFF)).isNotEmpty();
    }

    @Test
    void extract_recognizesComposeChoiceAsAlternativeReasonAndTradeoff() {
        String normalizedAnswerText = textNormalizer.normalize(
                "개발 환경은 쿠버네티스까지 올리지 않고 Docker Compose로 묶었습니다. "
                        + "새 팀원이 로컬을 빨리 띄우는 게 더 중요했고 운영 환경만 별도 파이프라인으로 분리했습니다. "
                        + "두 환경을 따로 맞춰야 하는 부담은 있었지만 온보딩은 훨씬 빨라졌습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ALTERNATIVE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.TRADEOFF)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
    }

    @Test
    void extract_recognizesCollaborationIssueAndClosureWhenReviewPointKeepsDiverging() {
        String normalizedAnswerText = textNormalizer.normalize(
                "배포 기준을 맞출 때 qa팀이랑 개발팀이 보는 포인트가 달라서 논의가 자꾸 길어졌습니다. "
                        + "이후 체크 항목도 같은 기준으로 맞췄고 같은 이슈로 다시 열리지 않았습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
    }

    @Test
    void extract_recognizesInterpretationDriftAndSharedCaseReasonInCollaboration() {
        String normalizedAnswerText = textNormalizer.normalize(
                "정산 기준을 맞출 때 데이터팀이랑 우리 팀 해석이 계속 달랐습니다. "
                        + "결국 같은 사례를 놓고 보니까 정리가 빨랐습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ISSUE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.REASON)).isNotEmpty();
    }

    @Test
    void extract_recognizesCauseAndActionWhenLockKeyOverlapIsFoundByTracingLogs() {
        String normalizedAnswerText = textNormalizer.normalize(
                "로그를 따라가 보니 락 키가 일부 작업에서 겹치고 있었습니다. "
                        + "저는 키 조합을 나눠서 다시 배포했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.CAUSE)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.ACTION)).isNotEmpty();
    }

    @Test
    void extract_recognizesVerificationAndResultWhenDuplicateRunsStopAfterReplayCheck() {
        String normalizedAnswerText = textNormalizer.normalize(
                "중복 실행은 멈췄습니다. 운영에서 같은 조건을 몇 번 더 돌려 보며 확인했습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.VERIFICATION)).isNotEmpty();
    }

    @Test
    void extract_recognizesActionResultAndPreventionForLockTimingFollowupClosure() {
        String normalizedAnswerText = textNormalizer.normalize(
                "락 키 계산을 다시 잡은 뒤 같은 문제가 다시 나오지 않았습니다. "
                        + "배치 변경 때 확인할 체크 포인트도 문서로 남겼습니다."
        );

        Map<GapType, List<String>> matchedPatterns = signalExtractor.extractMatchedPatterns(normalizedAnswerText);

        assertThat(matchedPatterns.get(GapType.ACTION)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.RESULT)).isNotEmpty();
        assertThat(matchedPatterns.get(GapType.PREVENTION)).isNotEmpty();
    }
}
