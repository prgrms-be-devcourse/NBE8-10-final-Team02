package com.back.backend.domain.github.analysis;

/**
 * 분석 파이프라인의 진행 상태.
 * Redis sync:status:{userId}:{repoId} 값으로 저장된다.
 */
public enum SyncStatus {
    PENDING,     // 동기화 요청 수신, 처리 대기 중
    IN_PROGRESS, // 처리 중 (step 필드로 세부 단계 표시)
    COMPLETED,   // 분석 완료
    SKIPPED,     // significance check 미달로 분석 생략
    FAILED       // 오류 발생
}
