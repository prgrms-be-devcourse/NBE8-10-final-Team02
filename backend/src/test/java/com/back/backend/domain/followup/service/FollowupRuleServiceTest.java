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
                        + "저는 백엔드 쪽 기본 CRUD와 배치 작업을 하면서 필요한 기능을 우선 붙였습니다. "
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
    void analyze_returnsNoFollowUpForProblemWhenCauseResultAndAlertPreventionAreExplicit() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "결제 승인 API에서 간헐적으로 타임아웃이 나는 장애를 해결한 경험이 있습니다. "
                        + "특정 시간대에만 응답이 길어져서 처음에는 외부 PG 문제로 봤는데, "
                        + "제가 슬로우 쿼리 로그와 커넥션 풀 상태를 같이 보니 주문 테이블의 락 대기가 원인이었습니다. "
                        + "승인 요청과 정산 업데이트가 같은 트랜잭션 안에 묶여 있어 락 점유 시간이 길어졌고, "
                        + "이를 승인 처리와 후속 정산 업데이트로 분리했습니다. "
                        + "수정 후에는 부하 테스트와 실제 배포 후 대시보드 지표를 같이 확인했고, "
                        + "p95 응답 시간이 2.1초에서 700ms 수준으로 내려갔습니다. "
                        + "이후에는 락 대기 경고 알람도 추가해서 같은 유형의 문제를 먼저 감지할 수 있게 했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, true);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
        assertThat(response.candidateQuestionTypes()).isEmpty();
    }

    @Test
    void analyze_returnsNoFollowUpForProblemWhenPatternReuseActsAsPrevention() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "이미지 업로드 서비스에서 캐시 스탬피드 때문에 CPU가 튀는 문제가 있었습니다. "
                        + "인기 이미지가 한꺼번에 만료되면 원본 스토리지 조회가 몰리는 구조였고, "
                        + "저는 만료 시점을 분산시키는 TTL jitter와 single-flight 방식의 중복 요청 방지 로직을 적용했습니다. "
                        + "변경 전후로 동일한 부하를 걸어 비교했고, 캐시 미스 구간 CPU 피크가 약 40% 줄었습니다. "
                        + "운영 적용 후 일주일 동안 알람도 같이 봤는데 재발은 없었고, 이후 비슷한 캐시 키에도 같은 패턴을 재사용했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
    }

    @Test
    void analyze_keepsProblemAsCandidateWhenOnlyVerificationDetailRemains() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "데이터 적재 배치가 아침마다 밀리는 문제가 있었습니다. "
                        + "확인해 보니 전날 누적 데이터가 많을수록 조인 범위가 커져서 배치 시간이 늘어나는 구조였습니다. "
                        + "그래서 저는 증분 기준 컬럼을 다시 잡고 쿼리를 나눠서 실행되게 바꿨습니다. "
                        + "처리 시간은 전보다 줄었지만, 정확히 어느 정도 줄었는지와 배포 후 장기적으로 안정화됐는지는 수치까지 챙기지는 못했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.CAUSE, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.signals()).containsEntry(GapType.PREVENTION, true);
        assertThat(response.signals()).containsEntry(GapType.VERIFICATION, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROBLEM_VERIFICATION_DETAIL
        );
    }

    @Test
    void analyze_returnsNoFollowUpForProblemWhenTestGuardAndNoRecurrenceAreExplicit() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROBLEM,
                "회원 탈퇴 처리에서 개인정보가 일부 테이블에 남는 문제가 있었습니다. "
                        + "원인을 추적해 보니 비동기 삭제 대상 테이블 목록이 오래된 코드와 문서에서 서로 달랐습니다. "
                        + "저는 삭제 대상 정의를 한곳으로 모으고 통합 테스트를 추가했습니다. "
                        + "배포 후 샘플 계정으로 여러 번 검증했고 누락이 재현되지 않았습니다. "
                        + "아주 큰 수치 개선을 말하긴 어렵지만, 컴플라이언스 이슈라서 팀 안에서는 꽤 중요하게 해결된 건으로 봤습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.PREVENTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
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
    void analyze_keepsTechAsEffectCandidateWhenMonolithChoiceAlreadyImpliesAlternativeAndTradeoff() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "초기 서비스에서는 MSA로 쪼개지 않고 모놀리스를 유지했습니다. "
                        + "팀 규모가 작고 도메인 경계도 아직 자주 바뀌어서, "
                        + "저는 배포와 디버깅 비용을 먼저 줄이는 쪽이 낫다고 봤습니다. "
                        + "다만 이 판단이 언제까지 유효한지나 나중에 어떤 시점에 분리 기준을 잡을지까지는 "
                        + "당시 명확히 정해 두진 않았습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, true);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.TECH_EFFECT_AFTER_ADOPTION
        );
    }

    @Test
    void analyze_returnsNoFollowUpForTechWhenComposeChoiceCoversAlternativeAndTradeoff() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.TECH,
                "초기 개발 환경에서는 Kubernetes 대신 Docker Compose를 썼습니다. "
                        + "서비스 수가 많지 않았고, 새 팀원이 로컬에서 빨리 띄우는 게 더 중요했습니다. "
                        + "DB, 캐시, API, 워커 정도만 묶으면 됐기 때문에 Compose가 가장 단순했습니다. "
                        + "운영 환경은 별도 배포 파이프라인으로 가져가고 로컬만 Compose로 제한해서 과한 추상화를 피했습니다. "
                        + "나중에 서비스 수가 늘어나면 바꿀 수 있다는 전제는 있었지만, 초기 온보딩 시간 단축에는 분명히 도움이 됐습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ALTERNATIVE, true);
        assertThat(response.signals()).containsEntry(GapType.TRADEOFF, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
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

    @Test
    void analyze_returnsNoFollowUpForProjectWhenConstraintDrivenReasonIsAlreadyExplained() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "반려동물 병원 예약 서비스를 출시한 경험이 있습니다. "
                        + "초기에는 전화 예약 비중이 높아서 예약 누락이 많았고, "
                        + "저는 백엔드 담당으로 예약 상태 머신과 알림 스케줄러를 맡았습니다. "
                        + "병원별 영업시간이 달라 예외 처리가 많았는데, 예약 가능 슬롯을 미리 계산하는 구조로 바꾸고 "
                        + "예약·취소·노쇼 상태를 이벤트 기반으로 관리했습니다. "
                        + "QA 단계에서는 실제 병원 운영팀과 시나리오를 돌려 보면서 예외 케이스를 계속 보완했고, "
                        + "출시 첫 달에 온라인 예약 비중이 20%대에서 60% 가까이 올라갔습니다. "
                        + "중복 예약 문의도 눈에 띄게 줄어서 이후 추가 지점 확장 때 같은 구조를 재사용했습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.REASON, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
    }

    @Test
    void analyze_keepsProjectAsCandidateWhenBriefSummaryWouldOtherwiseMatchDynamicWhitelist() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.PROJECT,
                "사내 재고 관리 시스템을 만드는 프로젝트가 있었는데, 기존 엑셀 작업을 옮겨오는 성격이라 요구사항이 자주 바뀌었습니다. "
                        + "저는 백엔드 쪽 기본 CRUD와 배치 작업을 맡아 필요한 기능을 우선 붙였습니다. "
                        + "일정은 맞췄지만 어떤 기준으로 우선순위를 잡았는지나 결과가 얼마나 안정화됐는지는 "
                        + "지금 설명하면 조금 일반론적으로 들릴 수 있습니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ROLE, true);
        assertThat(response.signals()).containsEntry(GapType.ACTION, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, false);
        assertThat(response.signals()).containsEntry(GapType.REASON, false);
        assertThat(response.finalAction()).isEqualTo(FinalAction.USE_CANDIDATE);
        assertThat(response.candidateQuestionTypes()).containsExactly(
                CandidateQuestionType.PROJECT_APPROACH_REASON,
                CandidateQuestionType.PROJECT_RESULT_DETAIL
        );
    }

    @Test
    void analyze_returnsNoFollowUpForCollaborationWhenDefinitionMismatchIsResolvedWithSharedEvidence() {
        FollowupAnalyzeResponse response = followupRuleService.analyze(new FollowupAnalyzeRequest(
                QuestionType.COLLABORATION,
                "정산 대시보드를 만들 때 데이터 분석가와 해석 기준이 자주 달라 협업이 쉽지 않았습니다. "
                        + "저는 백엔드에서 원천 데이터 생성 로직을 담당했고, 분석가는 대시보드 지표 정의를 맡고 있었습니다. "
                        + "논쟁이 반복되자 제가 샘플 데이터 20건을 뽑아 계산식과 실제 결과를 나란히 비교하는 검증 문서를 만들었고, "
                        + "그 문서로 어떤 예외를 포함할지 합의했습니다. "
                        + "이후 API 스펙도 그 정의에 맞춰 고정했고, 재오픈 이슈 없이 배포까지 갔습니다. "
                        + "협업이 잘 됐던 이유는 말로만 맞춘 게 아니라 같은 데이터를 기준으로 합의했기 때문이라고 생각합니다."
        ));

        assertThat(response.signals()).containsEntry(GapType.ISSUE, true);
        assertThat(response.signals()).containsEntry(GapType.RESULT, true);
        assertThat(response.finalAction()).isEqualTo(FinalAction.NO_FOLLOW_UP);
    }

    private FollowupRulesProperties loadProperties() {
        return FollowupRulesConfig.loadRules(new ClassPathResource("followup/rules/followup-rules-v0.2.yaml"));
    }
}
