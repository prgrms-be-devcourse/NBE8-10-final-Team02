create table interview_question_knowledge_tags (
    question_id bigint not null references interview_questions (id) on delete cascade,
    tag_id      bigint not null references knowledge_tags (id) on delete cascade,
    primary key (question_id, tag_id)
);
