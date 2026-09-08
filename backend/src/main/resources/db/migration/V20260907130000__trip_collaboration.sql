-- S15P21E201-294 · -299 · -320 · -330 · -332 · -338 — 동행자 초대·읽기 전용 공유·복제 씨앗.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만드는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
--   trip_invite       동행자 초대 표 한 장. 7일 뒤 만료. 수락하면 trip_member 에 행이 생긴다.
--   trip_member       (칸 셋 추가) 누가·어느 초대로·언제 초대했는지.
--   trip_share_link   읽기 전용 공유 주소. 30일 뒤 만료. 로그인 없이 열리고 열람 수를 센다.
--   trip_seed_place   공유 일정을 복제할 때 가져온 장소 구성. 새 여행의 추천이 이 장소를 앞세운다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 🔴 여기서 지키는 약속 셋
-- ══════════════════════════════════════════════════════════════════════════════
--
-- (1) 표(token)는 추측할 수 없는 난수다. 32바이트를 base64url 로 적으면 43글자다. 이 값이 이
--     표의 유일한 잠금이라 UNIQUE 로 두고, 순번이나 시각에서 파생한 값을 절대 넣지 않는다.
--
-- (2) 만료는 행을 지우는 것이 아니라 expires_at 을 지나는 것이다. 지난 표로 들어오면 만료(410)로
--     답해야 "이 링크가 있었는데 끝났다" 를 화면이 구분해 그릴 수 있다. 행을 지우면 404 와
--     구분이 안 된다.
--
-- (3) 여행이 지워지면 초대·공유 주소·씨앗도 함께 지워진다(ON DELETE CASCADE). 계정 삭제
--     (AccountDeletionService)가 여행 행을 직접 DELETE 하는데, 2026-09-05 에 새 외래키 하나가
--     그 삭제를 통째로 깨뜨린 적이 있다(itinerary_versions.created_by). 그 자리를 여기서
--     다시 만들지 않는다 — 여행에 매달린 것은 CASCADE, 사람을 가리키는 보조 칸은 SET NULL.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만들지 않는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 편집 이력 표는 없다. "누가 언제 무엇을 바꿨나" 는 itinerary_versions 가 판마다 이미 갖고
-- 있다(created_by · created_at · operation). 같은 사실을 두 곳에 적지 않는다.
-- 초대 취소(revoke) 칸도 없다 — 티켓이 요구하지 않고, 만료 시각을 당기는 것으로 같은 효과를
-- 낼 수 있다. 필요해지면 그때 더한다.

-- ── 동행자 초대 표 (F-COL-01 · S15P21E201-294 · -299) ─────────────────────────
CREATE TABLE trip_invite (
    trip_invite_id  UUID          PRIMARY KEY,
    trip_id         UUID          NOT NULL,

    token           VARCHAR(64)   NOT NULL,
    -- 초대받는 사람이 갖게 될 역할. OWNER 는 초대로 생기지 않는다(TripMember.invited 가 막는다).
    role            VARCHAR(10)   NOT NULL,

    created_by      UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_trip_invite_trip    FOREIGN KEY (trip_id)    REFERENCES trip (trip_id)      ON DELETE CASCADE,
    CONSTRAINT fk_trip_invite_creator FOREIGN KEY (created_by) REFERENCES app_user (user_id)  ON DELETE CASCADE,
    CONSTRAINT uq_trip_invite_token   UNIQUE (token),
    CONSTRAINT ck_trip_invite_role    CHECK (role IN ('EDITOR', 'VIEWER')),
    CONSTRAINT ck_trip_invite_expiry  CHECK (expires_at > created_at)
);

CREATE INDEX ix_trip_invite_trip ON trip_invite (trip_id, created_at DESC);

COMMENT ON TABLE trip_invite IS
    '동행자 초대 표 한 장. token 이 URL 에 실린다. 만료는 expires_at 을 지나는 것이고 행은 남는다.';
COMMENT ON COLUMN trip_invite.token IS
    '32바이트 난수의 base64url(43글자). 이 값이 유일한 잠금이다 — 순번·시각에서 파생하지 않는다.';

-- ── trip_member 에 초대 흔적 세 칸 ──────────────────────────────────────────────
--
-- S15P21E201-299 완료 기준 "누가·어떤 역할로·언제 초대됐고 언제 들어왔는지 남긴다".
-- joined_at 은 이미 있다. 초대한 사람·초대 표·초대 시각을 더한다. 소유자 행은 셋 다 NULL 이다.
ALTER TABLE trip_member ADD COLUMN trip_invite_id UUID;
ALTER TABLE trip_member ADD COLUMN invited_by     UUID;
ALTER TABLE trip_member ADD COLUMN invited_at     TIMESTAMPTZ;

ALTER TABLE trip_member
    ADD CONSTRAINT fk_trip_member_invite  FOREIGN KEY (trip_invite_id) REFERENCES trip_invite (trip_invite_id) ON DELETE SET NULL;
ALTER TABLE trip_member
    ADD CONSTRAINT fk_trip_member_inviter FOREIGN KEY (invited_by)     REFERENCES app_user (user_id)          ON DELETE SET NULL;

COMMENT ON COLUMN trip_member.invited_by IS
    '이 사람을 초대한 사람. 초대한 사람이 탈퇴하면 NULL 이 된다 — 참여 자체는 남는다.';

-- ── 읽기 전용 공유 주소 (F-COL-03 · S15P21E201-330 · -332) ────────────────────
CREATE TABLE trip_share_link (
    trip_share_link_id UUID        PRIMARY KEY,
    trip_id            UUID        NOT NULL,

    token              VARCHAR(64) NOT NULL,

    created_by         UUID        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,

    -- 몇 번 열렸나. 조회와 같은 트랜잭션에서 +1 한다 — 따로 세면 조회는 됐는데 수가 안 오른 상태가 생긴다.
    view_count         INTEGER     NOT NULL DEFAULT 0,
    last_viewed_at     TIMESTAMPTZ,

    CONSTRAINT fk_trip_share_link_trip    FOREIGN KEY (trip_id)    REFERENCES trip (trip_id)     ON DELETE CASCADE,
    CONSTRAINT fk_trip_share_link_creator FOREIGN KEY (created_by) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT uq_trip_share_link_token   UNIQUE (token),
    CONSTRAINT ck_trip_share_link_expiry  CHECK (expires_at > created_at),
    CONSTRAINT ck_trip_share_link_views   CHECK (view_count >= 0)
);

CREATE INDEX ix_trip_share_link_trip ON trip_share_link (trip_id, created_at DESC);

COMMENT ON TABLE trip_share_link IS
    '읽기 전용 공유 주소. 로그인 없이 열린다. 응답은 SharedItineraryResponse 만 쓴다 — 출발지 좌표·연락처·예산 상세 칸이 아예 없는 별도 모양이다.';

-- ── 복제 씨앗 (F-COL-04 · S15P21E201-338) ─────────────────────────────────────
--
-- 공유 일정에서 "장소 구성만" 가져온다. 시간표·예산·인원은 가져오지 않는다 — 그 값은 요청자의
-- 조건으로 새로 계산한다. 그래서 여기에는 장소와 원본 순서만 있다.
CREATE TABLE trip_seed_place (
    trip_id              UUID        NOT NULL,
    place_id             UUID        NOT NULL,
    -- 원본 일정에서의 방문 순서(1부터). 추천이 후보를 앞세울 때 이 순서를 쓴다.
    sequence             INTEGER     NOT NULL,

    -- 어디서 왔는지. 원본 여행이 지워지면 NULL 이 된다 — 복제된 여행은 그대로 남는다.
    source_trip_id       UUID,
    source_share_link_id UUID,

    created_at           TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_trip_seed_place              PRIMARY KEY (trip_id, place_id),
    CONSTRAINT fk_trip_seed_place_trip         FOREIGN KEY (trip_id)              REFERENCES trip (trip_id)                        ON DELETE CASCADE,
    CONSTRAINT fk_trip_seed_place_place        FOREIGN KEY (place_id)             REFERENCES place (place_id),
    CONSTRAINT fk_trip_seed_place_source_trip  FOREIGN KEY (source_trip_id)       REFERENCES trip (trip_id)                        ON DELETE SET NULL,
    CONSTRAINT fk_trip_seed_place_source_link  FOREIGN KEY (source_share_link_id) REFERENCES trip_share_link (trip_share_link_id) ON DELETE SET NULL,
    CONSTRAINT ck_trip_seed_place_sequence     CHECK (sequence >= 1)
);

COMMENT ON TABLE trip_seed_place IS
    '공유 일정을 복제할 때 가져온 장소 구성. 시간표·예산·인원은 없다 — 그것은 요청자 조건으로 새로 계산한다.';
