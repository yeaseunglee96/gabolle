-- S15P21E201-2027 · -2028 — 루트 포스트(내 여행 경로를 피드 글로 올리기)와 장소별 루트 추천.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만드는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
--   route_post           여행 일정(하루 또는 전체)을 올린 글 한 건. 올린 순간의 사본(스냅숏)이다 —
--                        원래 여행을 고치거나 지워도 글은 그대로다. 남이 내 일정 표를 직접 읽지 않는다.
--   route_post_stop      그 글의 들른 곳, 순서대로. place_id 색인이 「이 장소가 들어간 루트」(-2028)의 길이다.
--   route_post_reaction  좋아요·저장·「이 루트로 여행 만들기」. 한 사람이 한 글에 종류마다 한 줄 —
--                        같은 사람이 여러 번 복사해도 인기는 한 번만 오른다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 🔴 지키는 약속
-- ══════════════════════════════════════════════════════════════════════════════
--
-- (1) 숙소는 들어오지 않는다. 숙소(category = 'LODGING') 정류장, 숙소에서 나오고 들어가는 이동선,
--     하루의 출발점·돌아가는 곳은 사본을 만들 때 서버가 빼고 넣는다. 「어디서 잤나」가 곧 그 사람이
--     머문 정확한 위치이기 때문이다. 이 표에는 그것을 받을 칸이 없다.
-- (2) 공개 글만 있다. visibility 는 PUBLIC 하나만 허용한다 — 루트는 남이 따라 하라고 올리는 것이다.
--     안 보이게 하려면 지운다(deleted_at). 기록(story)의 나만 보기·팔로워 공개는 여기서 다루지 않는다.
-- (3) 인기는 세어 둔다(like_count·save_count·copy_count). 인기순 목록이 매번 반응 표를 모아 세지 않게.

CREATE TABLE route_post (
    route_post_id      UUID          PRIMARY KEY,
    author_user_id     UUID          NOT NULL,
    -- 어느 여행에서 왔나. 여행을 지워도 글은 남는다(사본이므로).
    trip_id            UUID,
    itinerary_id       VARCHAR(64),
    -- 0부터 센 하루. NULL 이면 일정 전체다.
    day_index          INTEGER,
    day_count          INTEGER       NOT NULL,
    title              VARCHAR(80)   NOT NULL,
    body               VARCHAR(500)  NOT NULL DEFAULT '',
    visibility         VARCHAR(20)   NOT NULL DEFAULT 'PUBLIC',
    -- 이동 시간 + 머무는 시간의 합(분), 이동 거리 합(미터). 모르면 NULL.
    duration_min       INTEGER,
    distance_m         INTEGER,
    -- 지도에 그릴 선. [[lat,lng],...] — 숙소 구간을 뺀 뒤 점을 솎아 둔다.
    path               JSONB         NOT NULL DEFAULT '[]'::jsonb,
    stop_count         INTEGER       NOT NULL,
    like_count         INTEGER       NOT NULL DEFAULT 0,
    save_count         INTEGER       NOT NULL DEFAULT 0,
    copy_count         INTEGER       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT fk_route_post_author FOREIGN KEY (author_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_route_post_trip   FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE SET NULL,
    CONSTRAINT ck_route_post_visibility CHECK (visibility = 'PUBLIC'),
    CONSTRAINT ck_route_post_counts CHECK (like_count >= 0 AND save_count >= 0 AND copy_count >= 0),
    CONSTRAINT ck_route_post_stops CHECK (stop_count >= 2)
);

CREATE INDEX ix_route_post_recent ON route_post (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_route_post_author ON route_post (author_user_id, created_at DESC);

COMMENT ON TABLE route_post IS
    '여행 일정을 피드 글로 올린 사본(-2027). 숙소 위치는 넣지 않는다. 공개 글만 있다.';

CREATE TABLE route_post_stop (
    route_post_id   UUID          NOT NULL,
    seq             INTEGER       NOT NULL,
    day_index       INTEGER       NOT NULL,
    place_id        UUID          NOT NULL,
    -- 올린 순간의 이름. 장소 이름이 바뀌어도 읽을 수 있게 남긴다(지금 이름은 place 를 다시 읽는다).
    name            VARCHAR(200)  NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    stay_minutes    INTEGER,

    CONSTRAINT pk_route_post_stop PRIMARY KEY (route_post_id, seq),
    CONSTRAINT fk_route_post_stop_post FOREIGN KEY (route_post_id) REFERENCES route_post (route_post_id) ON DELETE CASCADE,
    CONSTRAINT fk_route_post_stop_place FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE CASCADE
);

-- 「이 장소가 들어간 루트」(-2028)가 이 색인으로 찾는다.
CREATE INDEX ix_route_post_stop_place ON route_post_stop (place_id);

CREATE TABLE route_post_reaction (
    route_post_id   UUID          NOT NULL,
    user_id         UUID          NOT NULL,
    kind            VARCHAR(10)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_route_post_reaction PRIMARY KEY (route_post_id, user_id, kind),
    CONSTRAINT fk_route_post_reaction_post FOREIGN KEY (route_post_id) REFERENCES route_post (route_post_id) ON DELETE CASCADE,
    CONSTRAINT fk_route_post_reaction_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_route_post_reaction_kind CHECK (kind IN ('LIKE', 'SAVE', 'COPY'))
);

CREATE INDEX ix_route_post_reaction_user ON route_post_reaction (user_id, kind, created_at DESC);
