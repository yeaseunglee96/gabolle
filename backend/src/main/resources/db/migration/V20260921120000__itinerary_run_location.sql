-- 여행 진행을 GPS 로 따라간다 — 마지막 위치와 궤적.
--
-- 🔴 새 세션 표를 만들지 않는다. 진행 상태의 정본은 이미 itinerary_run 이고, 옆에 하나 더
--    만들면 「지금 몇 번째냐」가 두 벌이 된다. 여기서는 그 표에 위치를 붙이기만 한다.

-- ── 1. 마지막으로 받은 위치 ────────────────────────────────────────────────
-- 앱을 껐다 켰을 때 「어디쯤이었나」를 궤적 전체를 훑지 않고 한 번에 안다.
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_lat         DOUBLE PRECISION;
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_lng         DOUBLE PRECISION;
ALTER TABLE itinerary_run ADD COLUMN IF NOT EXISTS last_location_at TIMESTAMPTZ;

-- 좌표는 둘 다 있거나 둘 다 없어야 한다. 하나만 있는 행은 「있음」도 「모름」도 아니라,
-- 읽는 쪽이 나머지 하나를 0 으로 채우게 된다 — 0,0 은 아프리카 앞바다다.
ALTER TABLE itinerary_run DROP CONSTRAINT IF EXISTS ck_itinerary_run_last_location;
ALTER TABLE itinerary_run ADD CONSTRAINT ck_itinerary_run_last_location
    CHECK ((last_lat IS NULL) = (last_lng IS NULL));

COMMENT ON COLUMN itinerary_run.last_location_at IS
    '마지막으로 받은 위치의 기기 시각. 좌표가 없으면 NULL 이고, NULL 은 「아직 못 받았다」다.';

-- ── 2. 궤적 ────────────────────────────────────────────────────────────────
-- 🔴 버리는 로그가 아니다. 나중에 「구간별 실제 이동시간」과 「장소별 체류시간」을 여기서 낸다 —
--    지금 저장소에는 장소별 체류시간이 아예 없고, 일정은 하루를 곳 수로 나눠 쓰고 있다.
CREATE TABLE itinerary_run_ping (
    itinerary_run_ping_id UUID             PRIMARY KEY,
    itinerary_id          UUID             NOT NULL,

    lat                   DOUBLE PRECISION NOT NULL,
    lng                   DOUBLE PRECISION NOT NULL,

    -- 기기가 그 점을 찍은 시각.
    recorded_at           TIMESTAMPTZ      NOT NULL,
    -- 서버가 그 점을 받은 시각. 배치로 모아 올리면 위와 몇 분씩 벌어진다 —
    -- 하나만 두면 「기기 시계가 틀렸나」와 「망이 끊겼었나」를 영영 못 가른다.
    received_at           TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT fk_itinerary_run_ping_run
        FOREIGN KEY (itinerary_id) REFERENCES itinerary_run (itinerary_id) ON DELETE CASCADE,

    CONSTRAINT ck_itinerary_run_ping_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_itinerary_run_ping_lng CHECK (lng BETWEEN -180 AND 180),

    -- 같은 점을 두 번 올려도 한 번만 남는다. 배치 업로드는 재시도가 정상 경로다.
    CONSTRAINT uq_itinerary_run_ping UNIQUE (itinerary_id, recorded_at)
);

CREATE INDEX ix_itinerary_run_ping_run ON itinerary_run_ping (itinerary_id, recorded_at);

COMMENT ON TABLE itinerary_run_ping IS
    '여행 진행 중의 위치 궤적. 🔴 사람이 언제 어디 있었는지의 기록이라 이 저장소에서 제일 '
    '민감한 자료다. 탈퇴하면 연쇄 삭제에 기대지 말고 명시적으로 지운다. 보존은 여행 종료 후 '
    '1년이고 별도 정리 잡이 지운다 — 여행에 계절성이 있어 한 해치가 있어야 분석이 된다는 판단이다.';
