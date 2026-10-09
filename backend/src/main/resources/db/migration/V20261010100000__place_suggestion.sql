-- S15P21E201-2031 — 현지인이 장소·맛집을 직접 제안한다.
--
-- 🔴 제안은 place 표에 바로 넣지 않는다. 아무도 안 본 이름·좌표가 추천 후보가 되기 때문이다.
--    여기(place_suggestion)에 PENDING 으로 쌓이고, 운영자(ADMIN)가 승인하면 그때 place 에
--    CURATED · source_type = 'USER' · source_id = 제안 번호 로 한 줄 생긴다(place_id 칸에 그 번호를 남긴다).
--    거절은 지우지 않고 REJECTED 로 남긴다 — 같은 사람이 같은 것을 다시 올렸는지 볼 수 있게.
--
-- 사진(photo_url)은 제안한 사람이 /api/v1/uploads/story-image 로 직접 올린 것만 받는다(서비스가 확인한다).
-- 고아 사진 청소(UploadedImageRepository.findOrphansOlderThan)가 이 칸도 「붙은 자리」로 본다.

create table place_suggestion (
    place_suggestion_id uuid primary key,
    suggester_user_id   uuid         not null references app_user (user_id) on delete cascade,
    name                varchar(60)  not null,
    category            varchar(50)  not null,
    lat                 double precision not null,
    lng                 double precision not null,
    reason              varchar(80)  not null,
    photo_url           varchar(500),
    opening_hours       varchar(120),
    phone               varchar(30),
    status              varchar(20)  not null default 'PENDING',
    reject_reason       varchar(200),
    reviewed_by         uuid,
    reviewed_at         timestamptz,
    place_id            uuid references place (place_id) on delete set null,
    created_at          timestamptz  not null,
    constraint ck_place_suggestion_status check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_place_suggestion_approved_place check (status <> 'APPROVED' or place_id is not null)
);

-- 검토 목록(대기 중, 오래된 것부터)과 사람별 하루 상한 세기.
create index ix_place_suggestion_status_created on place_suggestion (status, created_at);
create index ix_place_suggestion_suggester_created on place_suggestion (suggester_user_id, created_at);
create index ix_place_suggestion_place on place_suggestion (place_id);
