package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.dto.request.FollowupAnalyzeRequest;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

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
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> propertySources = loader.load(
                    "followup-rules",
                    new ClassPathResource("followup-rules-v0.2.yaml")
            );
            MutablePropertySources mutablePropertySources = new MutablePropertySources();
            propertySources.forEach(mutablePropertySources::addLast);

            return new Binder(ConfigurationPropertySources.from(mutablePropertySources))
                    .bind("followup", Bindable.of(FollowupRulesProperties.class))
                    .orElseThrow(() -> new IllegalStateException("followup rules binding failed"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
