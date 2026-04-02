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
