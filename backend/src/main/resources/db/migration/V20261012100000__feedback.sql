-- S15P21E201-2049 — 의견 보내기(버그·건의·기타).
--
-- 로그인 없이도 받는다. 그래서 user_id 는 비어 있을 수 있고, app_user 에 외래키를 걸지 않는다 —
-- 탈퇴는 app_user 행을 익명화만 하므로 걸어도 CASCADE 가 안 돈다. 처리 기록은 문의 처리 목적으로 남긴다.
-- client_key 는 하루 상한을 세는 열쇠다: 로그인했으면 'u:<계정>', 아니면 기기 번호나 IP 의 SHA-256 앞부분.
-- IP·기기 번호 원문은 남기지 않는다.

create table feedback (
    feedback_id      uuid primary key,
    user_id          uuid,
    client_key       varchar(80)   not null,
    type             varchar(10)   not null,
    content          varchar(1000) not null,
    reply_email      varchar(254),
    screenshot_urls  text,
    app_version      varchar(40),
    platform         varchar(20),
    os_version       varchar(60),
    language         varchar(16),
    screen           varchar(120),
    trip_id          varchar(64),
    status           varchar(12)   not null default 'RECEIVED',
    admin_reply      varchar(1000),
    jira_key         varchar(40),
    handled_by       uuid,
    handled_at       timestamptz,
    created_at       timestamptz   not null,
    constraint ck_feedback_type check (type in ('BUG', 'IDEA', 'OTHER')),
    constraint ck_feedback_status check (status in ('RECEIVED', 'IN_PROGRESS', 'DONE'))
);

create index ix_feedback_status_created on feedback (status, created_at);
create index ix_feedback_client_created on feedback (client_key, created_at);
