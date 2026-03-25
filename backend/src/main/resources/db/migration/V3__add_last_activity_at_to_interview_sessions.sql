alter table interview_sessions
    add column last_activity_at timestamptz;

update interview_sessions
set last_activity_at = started_at
where started_at is not null
  and last_activity_at is null;
