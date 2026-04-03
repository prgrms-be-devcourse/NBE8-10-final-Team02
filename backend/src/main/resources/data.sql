-- knowledge_tags 초기 데이터 (dev 환경용 - 매 시작마다 실행)
-- !! 태그를 추가/수정할 때는
-- db/migration/V11__seed_knowledge_tags.sql KeywordTagExtractor.java(source of truth) 도 함께 변경하세요.
insert into knowledge_tags (name, category) values
    ('backend',          'domain'),
    ('computer science', 'domain'),
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
