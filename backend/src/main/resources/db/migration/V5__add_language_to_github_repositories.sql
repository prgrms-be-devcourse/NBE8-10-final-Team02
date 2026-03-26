-- github_repositories 테이블에 language 컬럼 추가
-- GitHub API의 primary language. 기존 행은 null로 초기화되며 다음 connection refresh 시 채워진다
alter table github_repositories
    add column language varchar(100);
