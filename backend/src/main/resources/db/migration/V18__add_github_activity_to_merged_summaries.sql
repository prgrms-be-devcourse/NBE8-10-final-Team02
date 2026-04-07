-- github_activity: GitHub Issue/PR 메타데이터 수집 결과 통계 (자소서/면접 포트폴리오용)
--
-- NULL 허용 이유:
--   - 이 컬럼 추가 이전에 생성된 merged_summaries 행은 NULL로 초기화된다.
--   - GitHub Metadata 수집을 생략한 경우(perRepoBudget < 4,000 등)에도 NULL.
--
-- 구조 예시:
--   {
--     "total_issues_created": 12,
--     "total_prs_authored": 8,
--     "total_prs_merged": 6,
--     "total_issues_completed": 12
--   }
alter table merged_summaries
    add column github_activity jsonb;
