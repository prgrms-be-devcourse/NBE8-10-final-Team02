package com.back.backend.domain.interview.service;

import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import org.springframework.stereotype.Component;

@Component
public class CandidateFollowupQuestionFactory {

    public FollowupQuestionDraft create(CandidateQuestionType candidateQuestionType) {
        if (candidateQuestionType == null) {
            return null;
        }

        return new FollowupQuestionDraft(
                InterviewQuestionType.FOLLOW_UP,
                DifficultyLevel.MEDIUM,
                switch (candidateQuestionType) {
                    case PROJECT_RESULT_DETAIL -> "그 결과가 실제로 어떻게 달라졌는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROJECT_RESULT_METRIC -> "그 결과를 어떤 지표나 수치로 확인했는지 설명해주실 수 있나요?";
                    case PROJECT_APPROACH_REASON -> "그 방식으로 접근한 이유를 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROJECT_SCOPE_NARROW -> "방금 설명하신 내용에서 본인이 직접 맡은 범위를 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROJECT_CORE_CLARIFY -> "그 프로젝트에서 본인이 맡은 핵심 역할과 기여를 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROBLEM_CAUSE_DETAIL -> "그 문제의 근본 원인을 어떻게 파악했는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROBLEM_VERIFICATION_DETAIL -> "그 원인을 어떤 방식으로 검증했는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case PROBLEM_PREVENTION_DETAIL -> "비슷한 문제가 다시 발생하지 않도록 어떤 예방책을 마련했는지 설명해주실 수 있나요?";
                    case PROBLEM_CORE_CLARIFY -> "그 문제 상황과 대응 과정을 조금 더 구체적으로 설명해주실 수 있나요?";
                    case TECH_ALTERNATIVE_COMPARE -> "그 기술을 선택할 때 다른 대안과는 어떻게 비교했는지 설명해주실 수 있나요?";
                    case TECH_TRADEOFF_DETAIL -> "그 기술 선택의 trade-off를 어떻게 판단했는지 설명해주실 수 있나요?";
                    case TECH_EFFECT_AFTER_ADOPTION -> "그 기술을 도입한 뒤 실제로 어떤 변화나 효과가 있었는지 설명해주실 수 있나요?";
                    case TECH_CORE_CLARIFY -> "그 기술을 왜 선택했고 어디에 적용했는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case COLLAB_ISSUE_DETAIL -> "협업 과정에서 어떤 이슈나 의견 충돌이 있었는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case COLLAB_AGREEMENT_CRITERIA -> "협업 과정에서 어떤 기준으로 합의했는지 설명해주실 수 있나요?";
                    case COLLAB_DECISION_REASON -> "그 결정을 내린 이유가 무엇이었는지 조금 더 구체적으로 설명해주실 수 있나요?";
                    case COLLAB_RESULT_IMPACT -> "그 협업 결과가 팀이나 프로젝트에 어떤 영향을 줬는지 설명해주실 수 있나요?";
                    case COLLAB_CORE_CLARIFY -> "그 협업 상황에서 본인의 역할과 기여를 조금 더 구체적으로 설명해주실 수 있나요?";
                }
        );
    }
}
