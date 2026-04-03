package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesConfig;
import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class FollowupRuleServiceTest {

    private FollowupRuleService followupRuleService;

    @BeforeEach
    void setUp() {
        FollowupRulesProperties properties = loadProperties();

        followupRuleService = new FollowupRuleService(
                new TextNormalizer(),
                new SignalExtractor(properties),
                new GapResolver(properties),
                new FinalActionDecider(properties),
                new CandidateQuestionSelector(properties)
        );
    }

    @Test
    void analyze_returnsUseDynamicForProjectWhitelistExactMatch() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "제가 맡아서 조회 API를 분리하고 배치 구조를 수정했습니다."
        ));

        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.RESULT);
        assertThat(response.secondaryGap()).isEqualTo(GapType.REASON);
        assertThat(response.orderedMissingGaps()).containsExactly(GapType.RESULT, GapType.REASON, GapType.METRIC);
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsUseDynamicForProjectWhenProjectOwnershipPhraseShowsRole() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "사내 정산 리포트 자동화 프로젝트를 맡은 적이 있습니다. "
                        + "이전에는 운영팀이 월말마다 SQL 결과를 손으로 정리해서 리포트를 만들었고, "
                        + "저는 백엔드에서 데이터 추출 배치와 리포트 생성 API를 설계했습니다. "
                        + "특히 컬럼 정의가 자주 바뀌어서 템플릿 엔진을 붙이고, 배치 실행 이력도 남기도록 만들었습니다. "
                        + "다만 당시에는 일정상 우선 자동 생성까지 열어두는 데 집중했고, "
                        + "어떤 범위까지 자동화할지나 운영팀 업무가 실제로 얼마나 줄었는지는 뒤에서 충분히 정리하지 못했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ROLE, true);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, false);
        assertThat(response.signals()).containsEntry(GapType.REASON, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.RESULT);
        assertThat(response.secondaryGap()).isEqualTo(GapType.REASON);
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_keepsProjectAsCandidateWhenAssignmentIsGenericTaskScope() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "사내 재고 관리 시스템을 만드는 프로젝트가 있었는데, 기존 엑셀 작업을 옮겨오는 성격이라 요구사항이 자주 바뀌었습니다. "
                        + "저는 백엔드 쪽 기본 CRUD와 배치 작업을 맡아 필요한 기능을 우선 붙였습니다. "
                        + "일정은 맞췄지만 어떤 기준으로 우선순위를 잡았는지나 결과가 얼마나 안정화됐는지는 "
                        + "지금 설명하면 조금 일반론적으로 들릴 수 있습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ROLE, false);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.primaryGap()).isEqualTo(GapType.ROLE);
        assertThat(response.secondaryGap()).isEqualTo(GapType.RESULT);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROJECT_CORE_CLARIFY,
                CandidateQuestionType.PROJECT_RESULT_DETAIL
        );
    }

    @Test
    void analyze_returnsUseCandidateWhenProjectScopeIsMixed() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "여러 프로젝트에서 제가 맡은 백엔드 구현을 했습니다."
        ));

        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROJECT_SCOPE_NARROW,
                CandidateQuestionType.PROJECT_RESULT_DETAIL
        );
        assertThat(response.signals()).containsEntry(GapType.SCOPE_MIXED, true);
    }

    @Test
    void analyze_returnsNoFollowUpWhenProblemMainGapsAreClosed() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "원인을 분석해 보니 캐시 키 충돌이었고, 제가 키 생성 로직을 수정했습니다. "
                        + "배포 후 로그로 검증했고 응답 시간이 120ms까지 줄었습니다. 재발 방지로 체크리스트도 추가했습니다."
        ));

        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
        assertThat(response.orderedMissingGaps()).isEmpty();
        assertThat(response.primaryGap()).isNull();
        assertThat(response.secondaryGap()).isNull();
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsUseDynamicForProblemWhenOnlyVerificationAndPreventionAreMissing() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "주문 중복 생성 이슈가 있었는데, 재현해 보니 카프카 컨슈머 재시도와 멱등키 처리 시점이 어긋나면서 "
                        + "같은 주문이 두 번 저장되는 경우였습니다. 저는 재현용 테스트 데이터를 따로 만들고, "
                        + "멱등 체크를 DB 쓰기 이후가 아니라 직전 단계로 당기는 방식으로 수정했습니다. "
                        + "원인과 수정 방향은 비교적 명확했는데, 당시에는 긴급 대응이어서 어떤 검증 기준으로 배포를 승인했는지와 "
                        + "이후 재발 방지까지는 충분히 정리하지 못했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.VERIFICATION);
        assertThat(response.secondaryGap()).isEqualTo(GapType.PREVENTION);
        assertThat(response.orderedMissingGaps()).containsExactly(
                GapType.VERIFICATION,
                GapType.PREVENTION,
                GapType.METRIC
        );
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsUseDynamicForProblemWhenCauseAndFixAreClearButVerificationAndPreventionAreMissing() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "추천 배치 서버 메모리가 계속 올라가서 결국 OOM으로 죽는 문제가 있었습니다. "
                        + "heap dump를 떠 보니 대용량 상품 객체가 변환 과정에서 중복으로 잡혀 있었고, "
                        + "저는 스트림 체인을 끊고 chunk 단위 처리로 바꿨습니다. "
                        + "원인은 꽤 명확하게 찾았고 수정도 바로 적용했는데, 배포 후 어떤 기준으로 충분히 해결됐다고 판단했는지와 "
                        + "이후 비슷한 실수를 막기 위한 장치까지는 설명이 더 필요한 답변이라고 생각합니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.VERIFICATION);
        assertThat(response.secondaryGap()).isEqualTo(GapType.PREVENTION);
        assertThat(response.orderedMissingGaps()).containsExactly(
                GapType.VERIFICATION,
                GapType.PREVENTION,
                GapType.METRIC
        );
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsUseDynamicForProblemWhenInvestigationAndActionAreSpecificButVerificationAndPreventionAreMissing() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "검색 응답이 특정 키워드에서만 급격히 느려지는 문제가 있었습니다. 단순 인덱스 이슈인 줄 알았는데, "
                        + "제가 실제 실행 계획과 검색어 패턴을 같이 보니 n-gram 인덱스와 정렬 조건이 충돌하는 케이스였습니다. "
                        + "그래서 인기 검색어는 별도 prefix 인덱스로 우회하고, 정렬 기준도 일부 조정했습니다. "
                        + "체감 성능은 좋아졌고 문의도 줄었지만, 정확히 어떤 기준으로 검색 품질 저하를 감수했는지와 "
                        + "장기적으로 재발을 막기 위한 운영 장치까지는 한 번 더 설명해야 할 것 같습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.VERIFICATION);
        assertThat(response.secondaryGap()).isEqualTo(GapType.PREVENTION);
        assertThat(response.orderedMissingGaps()).containsExactly(
                GapType.VERIFICATION,
                GapType.PREVENTION,
                GapType.METRIC
        );
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_keepsProblemAsCandidateWhenCauseAndVerificationAreOnlyMentionedAsMissing() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "배포 직후 로그인 오류가 난 적이 있는데, 우선 롤백하고 설정값을 다시 확인해서 빠르게 복구한 경험이 있습니다. "
                        + "저는 배포 담당자와 같이 로그를 보면서 문제 지점을 찾았고, 실제로는 환경 변수 차이 쪽이었던 걸로 기억합니다. "
                        + "다만 정확히 어떤 원인 구조였는지, 수정 후 어떻게 검증했는지는 자세히 말하면 더 확인이 필요한 답변입니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, false);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.primaryGap()).isEqualTo(GapType.CAUSE);
        assertThat(response.secondaryGap()).isEqualTo(GapType.VERIFICATION);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROBLEM_CAUSE_DETAIL,
                CandidateQuestionType.PROBLEM_VERIFICATION_DETAIL
        );
    }

    @Test
    void analyze_keepsProblemAsCandidateWhenMitigationExistsButRootCauseAndVerificationStayUnclear() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "외부 배송사 API가 불안정해서 송장 발급이 종종 실패한 적이 있습니다. "
                        + "그때는 일단 재시도 로직을 넣고 수동 처리 절차를 같이 운영했습니다. "
                        + "사용자는 크게 문제를 못 느끼게 막았지만, 근본 원인이 우리 쪽인지 외부 쪽인지 "
                        + "명확히 분리해서 검증한 사례라고 보긴 어렵습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, false);
        assertThat(response.signals()).containsEntry(GapType.RESULT, false);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.primaryGap()).isEqualTo(GapType.CAUSE);
        assertThat(response.secondaryGap()).isEqualTo(GapType.VERIFICATION);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROBLEM_CAUSE_DETAIL,
                CandidateQuestionType.PROBLEM_VERIFICATION_DETAIL
        );
    }

    @Test
    void analyze_returnsUseDynamicForTechWhenInsteadChoiceAndReasonArePresent() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "정산 리포트 구간에서는 JPA 대신 jOOQ를 선택했습니다. "
                        + "동적 조건과 집계가 많아서 SQL을 직접 제어할 수 있는 쪽이 유지보수에 낫다고 판단했습니다. "
                        + "실제로 초기 개발 속도는 괜찮았지만, 팀원들의 러닝커브와 이후 성능 이점은 더 설명이 필요합니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, true);
        assertThat(response.signals()).containsEntry(GapType.REASON, true);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.TRADEOFF);
        assertThat(response.secondaryGap()).isEqualTo(GapType.RESULT);
        assertThat(response.orderedMissingGaps()).containsExactly(GapType.TRADEOFF, GapType.RESULT);
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsUseDynamicForTechWhenAlternativesWereActuallyReviewed() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "주문 이벤트 처리 구조를 바꿀 때 Kafka, RabbitMQ, DB polling을 같이 검토했습니다. "
                        + "이벤트 양이 계속 늘고 재처리 이력이 중요해서 확장성과 replay 관점에서 Kafka 쪽으로 기울었습니다. "
                        + "다만 Kafka의 운영 복잡도를 어느 정도까지 감수할 수 있다고 본 건지와 실제 도입 효과는 더 설명이 필요합니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, true);
        assertThat(response.signals()).containsEntry(GapType.REASON, true);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_DYNAMIC);
        assertThat(response.primaryGap()).isEqualTo(GapType.TRADEOFF);
        assertThat(response.secondaryGap()).isEqualTo(GapType.RESULT);
    }

    @Test
    void analyze_keepsTechAsCandidateWhenComparisonAndDownsideAreOnlyMentionedAsMissing() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "클라이언트 화면이 많긴 했지만 결국 REST로 갔습니다. "
                        + "당시에는 팀에 익숙한 방식이었고, 서버 캐시나 권한 처리도 더 단순하게 가져가고 싶었습니다. "
                        + "GraphQL도 얘기는 했지만 어디까지 비교했고 어떤 단점을 감수한 건지는 깊게 설명한 사례는 아닙니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, false);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
    }

    @Test
    void analyze_doesNotTreatAskedButUnexplainedComparisonAsAlternativeSignal() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "백엔드는 Spring Boot, 프론트는 React를 썼습니다. "
                        + "팀원들이 가장 익숙한 조합이어서 빠르게 개발하기 좋다고 봤습니다. "
                        + "다른 대안을 어떻게 비교했는지나 이 선택의 단점을 깊게 따져본 답변은 아닙니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, false);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
    }

    private FollowupRulesProperties loadProperties() {
        return FollowupRulesConfig.loadRules(new ClassPathResource("followup-rules-v0.2.yaml"));
    }
}
