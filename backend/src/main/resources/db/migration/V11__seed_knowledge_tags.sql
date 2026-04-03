-- knowledge_tags 초기 데이터
-- !! 태그를 추가/수정할 때는
-- db/migration/V11__seed_knowledge_tags.sql KeywordTagExtractor.java(source of truth) 도 함께 변경하세요.
-- (data.sql은 dev 환경에서 매 시작마다 실행되어 태그를 항상 최신으로 유지합니다)
insert into knowledge_tags (name, category) values
    ('backend',          'domain'),
    ('computer science',               'domain'),
    ('interview',        'domain'),
    ('system-design',    'domain'),
    ('c',                'language'),
    ('java',             'language'),
    ('javascript',       'language'),
    ('python',           'language'),
    ('sql',              'language'),
    ('algorithm',        'topic'),
    ('behavioral',       'topic'),
    ('data-structure',   'topic'),
    ('database',         'topic'),
    ('design-pattern',   'topic'),
    ('docker',           'topic'),
    ('functional',       'topic'),
    ('git',              'topic'),
    ('network',          'topic'),
    ('oop',              'topic'),
    ('os',               'topic'),
    ('security',         'topic'),
    ('spring',           'topic'),
    ('testing',          'topic')
on conflict (name) do nothing;
