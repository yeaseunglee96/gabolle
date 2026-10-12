-- S15P21E201-2020 — 코스별 품질 피드백(좋아요/아쉬워요 + 이유).
--
-- 한 사람이 한 일정의 한 판(version)에 한 번 — 다시 고르면 같은 행을 고친다(upsert).
-- 일정을 고치면 판이 올라가므로 새 판에는 새 평가를 남길 수 있다. 화면은 가장 최근 평가를 보여 준다.
-- course_label(A/B/C)·area_code(trip.origin_area_code) 는 집계용으로 저장 시점 값을 복사해 둔다.
-- 일정·계정이 지워지면 평가도 같이 지운다(ON DELETE CASCADE) — 남겨 둘 이유가 없다.

create table course_feedback (
    course_feedback_id uuid primary key,
    user_id            uuid         not null references app_user (user_id) on delete cascade,
    itinerary_id       uuid         not null references itineraries (itinerary_id) on delete cascade,
    version            integer      not null,
    course_label       varchar(8),
    area_code          varchar(30),
    rating             varchar(8)   not null,
    reasons            varchar(200) not null default '',
    comment            varchar(200),
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    constraint uq_course_feedback unique (user_id, itinerary_id, version),
    constraint ck_course_feedback_rating check (rating in ('UP', 'DOWN'))
);

create index ix_course_feedback_updated on course_feedback (updated_at);
create index ix_course_feedback_itinerary on course_feedback (itinerary_id);
