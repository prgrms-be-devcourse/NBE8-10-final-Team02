package com.back.backend.domain.followup.service;

import com.back.backend.domain.followup.config.FollowupRulesConfig;
import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.GapType;
import com.back.backend.domain.followup.model.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinalActionDeciderTest {

    private FinalActionDecider finalActionDecider;

    @BeforeEach
    void setUp() {
        FollowupRulesProperties properties = FollowupRulesConfig.loadRules(
                new ClassPathResource("followup/rules/followup-rules-v0.2.yaml")
        );
        finalActionDecider = new FinalActionDecider(properties);
    }

    @Test
    void decide_returnsUseCandidateForBriefProjectSummaryGuard() {
        String normalizedAnswerText = "사내 재고 관리 시스템을 만드는 프로젝트가 있었는데, 기존 엑셀 작업을 옮겨오는 성격이라 요구사항이 자주 바뀌었습니다. "
                + "저는 백엔드 쪽 기본 crud와 배치 작업을 맡아 필요한 기능을 우선 붙였습니다. "
                + "일정은 맞췄지만 어떤 기준으로 우선순위를 잡았는지나 결과가 얼마나 안정화됐는지는 지금 설명하면 조금 일반론적으로 들릴 수 있습니다.";

        assertThat(normalizedAnswerText.length()).isBetween(160, 219);

        FinalAction finalAction = finalActionDecider.decide(
                QuestionType.PROJECT,
                projectWhitelistSignals(),
                projectResultReasonResolution(),
                normalizedAnswerText
        );

        assertThat(finalAction).isEqualTo(FinalAction.USE_CANDIDATE);
    }

    @Test
    void decide_keepsUseDynamicWhenProjectAnswerIsDetailedEnoughToEscapeBriefGuard() {
        String normalizedAnswerText = "사내 재고 관리 시스템 프로젝트에서 저는 백엔드 배치와 조회 api를 맡았습니다. "
                + "엑셀 이전 단계라 요구사항이 자주 바뀌어 우선순위를 계속 조정해야 했고, 상태 전이를 분리해 기능을 붙였습니다. "
                + "운영팀 요청을 반영해 관리자 보정 화면도 바로 열어 뒀습니다. "
                + "다만 어떤 기준으로 우선순위를 잡았는지와 결과가 얼마나 안정화됐는지는 추가 설명이 더 필요합니다.";

        assertThat(normalizedAnswerText.length()).isBetween(160, 219);

        FinalAction finalAction = finalActionDecider.decide(
                QuestionType.PROJECT,
                projectWhitelistSignals(),
                projectResultReasonResolution(),
                normalizedAnswerText
        );

        assertThat(finalAction).isEqualTo(FinalAction.USE_DYNAMIC);
    }

    private Map<GapType, Boolean> projectWhitelistSignals() {
        Map<GapType, Boolean> signals = new LinkedHashMap<>();
        for (GapType gapType : GapType.values()) {
            signals.put(gapType, false);
        }
        signals.put(GapType.ROLE, true);
        signals.put(GapType.ACTION, true);
        return signals;
    }

    private GapResolver.Resolution projectResultReasonResolution() {
        return new GapResolver.Resolution(
                List.of(GapType.RESULT, GapType.REASON),
                List.of(GapType.RESULT, GapType.REASON, GapType.METRIC),
                GapType.RESULT,
                GapType.REASON
        );
    }
}
