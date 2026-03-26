-- github_repositories 테이블에 pushed_at, owner_type 컬럼 추가
-- pushed_at: GitHub API의 pushed_at 값 (마지막으로 push된 시각). 기여/URL 추가 경로는 null
-- owner_type: 저장 시점에 결정 ('owner' | 'collaborator'). 기존 행은 null로 초기화된다
alter table github_repositories
    add column pushed_at  timestamptz,
    add column owner_type varchar(20);
