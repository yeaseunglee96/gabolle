-- 일정 진행 — 「지금 어느 단계인가」 (S15P21E201-1325, 시안 ⑤).
--
-- 🔴 도착 시각은 여기 안 담는다. itinerary_item_actual(V20260908…, S15P21E201-293)이 이미
--    그 자리다. 같은 사실을 두 표에 담으면 둘이 어긋나는 날이 오고, 그때 어느 쪽이 진짜인지
--    아무도 답할 수 없다. 이 표들은 그 위에 「달리고 있나」와 「무슨 일이 있었나」만 얹는다.
--
-- 🔴 여행이 아니라 **일정**에 붙인다. 인계 문서는 tripId 당이라고 적었지만, currentStopIndex
--    는 한 일정의 정차지 순서 안에서만 뜻이 있다. 일정은 고칠 때마다 새 판이 생기고 여행
--    하나에 일정이 여럿일 수 있어서, 여행에 붙이면 「몇 번째」가 어느 일정의 몇 번째인지
--    알 수 없게 된다.

CREATE TABLE itinerary_run (
    itinerary_id       UUID        PRIMARY KEY,
    trip_id            UUID        NOT NULL,

    -- PLANNED(아직) · RUNNING(달리는 중) · PAUSED(멈춤) · DONE(다 돌았다)
    status             VARCHAR(16) NOT NULL,

    -- 지금 향하고 있는 정차지의 자리(0부터). 다 돌았으면 정차지 수와 같다.
    current_stop_index INT         NOT NULL DEFAULT 0,

    started_at         TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_itinerary_run_status
        CHECK (status IN ('PLANNED', 'RUNNING', 'PAUSED', 'DONE')),
    CONSTRAINT ck_itinerary_run_index
        CHECK (current_stop_index >= 0),

    -- 일정이 지워지면 그 진행도 같이 지운다 — 남겨 두면 어느 일정의 것인지 못 읽는다.
    CONSTRAINT fk_itinerary_run_itinerary
        FOREIGN KEY (itinerary_id) REFERENCES itineraries (itinerary_id) ON DELETE CASCADE
);

COMMENT ON TABLE itinerary_run IS
    '일정 하나의 진행 상태. 도착 시각은 itinerary_item_actual 에 있고 여기에는 없다.';

-- 🔴 일어난 일은 **덮어쓰지 않고 쌓는다.** 상태 한 줄만 두면 「건너뛴 곳」과 「안 간 곳」을
--    가를 수 없고, 나중에 「거기 갔었나?」를 기억으로만 풀어야 한다. 건너뛴 곳은 안 간 곳이다.
CREATE TABLE itinerary_stop_event (
    itinerary_stop_event_id UUID        PRIMARY KEY,
    itinerary_id            UUID        NOT NULL,

    -- 🔴 판을 건너 살아남는 이름이다(ItineraryItem.itemKey). 항목 행 id 를 적으면 일정을
    --    한 번 고치는 순간 어제 남긴 기록이 어느 정차지의 것인지 알 수 없게 된다.
    --    START·PAUSE 는 정차지가 없는 사건이라 NULL 이다.
    item_key                UUID,

    -- START · PAUSE · ARRIVE_AUTO · ARRIVE_MANUAL · SKIP
    event_type              VARCHAR(20) NOT NULL,

    -- 그 일이 일어난 시각. 기록한 시각(created_at)과 다르다 — 신호가 늦게 올라올 수 있다.
    occurred_at             TIMESTAMPTZ NOT NULL,
    recorded_by             UUID        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_itinerary_stop_event_type
        CHECK (event_type IN ('START', 'PAUSE', 'ARRIVE_AUTO', 'ARRIVE_MANUAL', 'SKIP')),

    -- 정차지 사건에는 정차지가 있어야 하고, 출발·중지에는 없어야 한다. 섞이면 「어디서
    -- 건너뛰었나」를 못 읽는다.
    CONSTRAINT ck_itinerary_stop_event_key
        CHECK ((event_type IN ('START', 'PAUSE') AND item_key IS NULL)
            OR (event_type NOT IN ('START', 'PAUSE') AND item_key IS NOT NULL)),

    CONSTRAINT fk_itinerary_stop_event_itinerary
        FOREIGN KEY (itinerary_id) REFERENCES itineraries (itinerary_id) ON DELETE CASCADE
);

-- 한 일정의 사건을 시간 순으로 읽는 질의가 전부다.
CREATE INDEX ix_itinerary_stop_event_itinerary
    ON itinerary_stop_event (itinerary_id, occurred_at);

COMMENT ON TABLE itinerary_stop_event IS
    '일정 진행 중 일어난 일. 덮어쓰지 않고 쌓는다 — 건너뛴 곳과 안 간 곳을 가르기 위해.';
