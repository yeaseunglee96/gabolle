-- S15P21E201-461 — 여행 참여자·멱등 키 표. 지금까지 InMemory 로만 남아 있던 마지막 둘이다.
--
-- 고지혁 님 실측(2026-09-04) — 여행은 저장되는데 취향·제약이 조용히 버려지고 있었다.
-- 그 표(preference_answer·constraint_answer)는 이미 V120000 에 있어서 이 파일은
-- trip_member·trip_idempotency 만 채운다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 여행 참여자 — ERD 의 TRIP_MEMBERS. TripMember(도메인)가 가리키는 표인데 없었다.
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 만든 사람도 반드시 OWNER 로 여기 들어간다 — 조회 권한 판정(TripQueryService)이
--    이 표를 본다는 전제로 짜여 있다. 안 들어가면 자기 여행을 못 본다.
CREATE TABLE trip_member (
    trip_member_id UUID        PRIMARY KEY,
    trip_id        UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    role           VARCHAR(10) NOT NULL,
    joined_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_trip_member_trip FOREIGN KEY (trip_id) REFERENCES trip (trip_id),
    CONSTRAINT fk_trip_member_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),

    CONSTRAINT uq_trip_member UNIQUE (trip_id, user_id),
    CONSTRAINT ck_trip_member_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

-- 여행마다 OWNER 는 정확히 하나다(TripMember.invited 가 OWNER 생성을 도메인에서부터 막는다).
CREATE UNIQUE INDEX uq_trip_member_one_owner
    ON trip_member (trip_id) WHERE role = 'OWNER';

CREATE INDEX ix_trip_member_user ON trip_member (user_id);

-- ══════════════════════════════════════════════════════════════════════════════
-- 멱등 키 — TripRepository.saveWithIdempotency (API-09)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 trip_id 에 외래키를 걸지 않는다. 이긴 요청과 진 요청이 동시에 경쟁할 때, 이 표에
--    먼저 INSERT ... ON CONFLICT DO NOTHING 으로 승부를 낸 다음에야 trip 을 저장하므로,
--    이 INSERT 시점에는 trip 이 아직 없을 수 있다(itinerary_versions 의 같은 패턴 —
--    JpaItineraryRepository 참고, SAVEPOINT 대신 이 방식을 쓴 이유가 같다).
CREATE TABLE trip_idempotency (
    user_id              UUID         NOT NULL,
    idempotency_key      VARCHAR(255) NOT NULL,
    -- SHA-256 hex = 64자 (TripCreationService.fingerprintOf).
    request_fingerprint  VARCHAR(64)  NOT NULL,
    trip_id              UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (user_id, idempotency_key)
);
