package com.back.backend.domain.github.analysis;

/**
 * 커밋의 파이프라인 포함 여부.
 *
 * 분류 기준은 {@link CommitClassifier}에서 관리한다.
 * 카테고리(evidenceBullets/challenges/techDecisions) 세분화는 AI에 위임한다.
 */
public enum CommitCategory {

    /** AI에 전달할 커밋 */
    INCLUDED,

    /**
     * docs/chore/style/ci/test/build 등 AI 분석 불필요 커밋.
     * diff 조회 없이 파이프라인에서 제거한다.
     */
    IGNORED
}
