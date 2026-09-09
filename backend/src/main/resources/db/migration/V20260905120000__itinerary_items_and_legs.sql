-- S15P21E201-604 — 추천이 만든 후보를 실제로 담을 곳을 만든다.
--
-- V20260903150000(-313)이 24~31행에 남긴 약속이 이 파일이다 — "실제 일정 항목·구간은
-- 추천 계산이 붙는 티켓에서 그 모양이 확정된 뒤 만든다." 지금이 그 티켓이다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 일정 항목 — 판마다 복사되는 스냅샷
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 부모가 itinerary_id 가 아니라 itinerary_version_id 인 이유.
--    itinerary_versions 는 "덮어쓰지 않는 스냅샷"이다(V150000). 항목을 itinerary_id 에
--    매달면 v2 를 만들며 항목을 고치는 순간 v1 이 가리키던 내용이 소급해서 바뀐다 —
--    그러면 그 "덮어쓰지 않는다"는 약속이 항목 앞에서는 거짓이 된다. 그래서 판을 새로
--    만들 때마다 항목도 그 판의 것으로 복사한다. 3박 일정이라 해 봐야 하루 최대 몇 개,
--    많아야 십수 행이라 복사 비용은 문제가 되지 않는다.
CREATE TABLE itinerary_item (
    itinerary_item_id    UUID          PRIMARY KEY,
    itinerary_version_id UUID          NOT NULL,

    -- 🔴 판을 건너 같은 항목을 가리키는 열쇠. ItineraryEditController 가 URL 로 받는
    --    itemId 가 이 값이다 — PK(itinerary_item_id)는 판마다 새로 생겨서 그 자리를
    --    대신할 수 없다("이 항목을 고정한다" 같은 편집이 어느 판의 어느 행을 가리켜야
    --    하는지 판을 건너 추적할 수 없어진다). 판을 복사할 때는 이 값을 그대로 물려준다.
    item_key             UUID          NOT NULL,

    day_index             INTEGER      NOT NULL,
    visit_date            DATE         NOT NULL,
    sequence              INTEGER      NOT NULL,

    place_id              UUID         NOT NULL,

    -- 🔴 전부 NULL 허용이다. 프리셋(time_window_preset, V20260904010000)을 실제 시각으로
    --    바꾸는 규칙이 아직 확정되지 않았다. 09:00~18:00 같은 값을 지어내지 않는다 —
    --    모르면 모른다고 남긴다.
    start_time            TIME,
    end_time              TIME,
    stay_minutes          INTEGER,

    locked                BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 🔴 NULL 허용. place 표에 비용 칸이 없다(V20260904000000). 0 을 넣으면 "공짜"로
    --    읽히는데 그것은 다른 사실이다 — 몰라서 못 채운 것과 값이 0인 것은 구분돼야 한다.
    estimated_cost_krw    INTEGER,

    data_status           VARCHAR(20)  NOT NULL,

    reason_codes          VARCHAR(64)[] NOT NULL DEFAULT '{}',
    warning_codes         VARCHAR(64)[] NOT NULL DEFAULT '{}',

    -- 사용자가 손으로 넣은 항목은 이 값이 없다(NULL). recommendation_job.request_id 가
    -- UNIQUE 라서 이 칸의 FK 대상이 될 수 있다.
    source_request_id     UUID,

    created_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_itinerary_item_version
        FOREIGN KEY (itinerary_version_id) REFERENCES itinerary_versions (itinerary_version_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_item_place
        FOREIGN KEY (place_id) REFERENCES place (place_id),
    -- 🔴 ON DELETE SET NULL 인 이유. 이 칸은 "이 항목이 어느 추천에서 나왔나" 를 적는
    --    출처 표시일 뿐, 항목이 존재할 근거가 아니다. RESTRICT(기본값)로 두면 추천 기록을
    --    지우는 모든 경로가 이 칸에 막힌다 — 실제로 계정 삭제(AccountDeletionService)가
    --    추천 작업을 지우다 외래키 위반으로 통째로 실패했다. 출처를 잃는 것이 지울 수
    --    없는 것보다 낫다.
    -- 🔴 DEFERRABLE INITIALLY DEFERRED 인 이유. 한 트랜잭션 안에서 일정과 추천 작업이
    --    함께 저장되는데 순서를 한쪽으로 고정할 수가 없다. 작업 행을 먼저 쓰면
    --    ck_recommendation_job_result_present(SUCCEEDED 면 itinerary_id 필수)가 막고,
    --    일정을 먼저 쓰면 이 외래키가 막는다 — CHECK 는 지연시킬 수 없고 외래키는 된다.
    --    그래서 이쪽을 커밋 시점으로 미룬다. 검사를 없애는 것이 아니라 시점만 옮기는 것이고,
    --    트랜잭션이 끝날 때 둘 다 없으면 그때 거부된다.
    CONSTRAINT fk_itinerary_item_source_request
        FOREIGN KEY (source_request_id) REFERENCES recommendation_job (request_id)
        ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT uq_itinerary_item_slot UNIQUE (itinerary_version_id, day_index, sequence),
    CONSTRAINT uq_itinerary_item_key  UNIQUE (itinerary_version_id, item_key),

    CONSTRAINT ck_itinerary_item_day      CHECK (day_index >= 0),
    CONSTRAINT ck_itinerary_item_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_itinerary_item_stay     CHECK (stay_minutes IS NULL OR stay_minutes > 0),
    CONSTRAINT ck_itinerary_item_cost     CHECK (estimated_cost_krw IS NULL OR estimated_cost_krw >= 0),

    -- 반쪽 시간은 시간이 아니다. place.ck_place_origin_pair 와 같은 논리다.
    CONSTRAINT ck_itinerary_item_time_pair
        CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CONSTRAINT ck_itinerary_item_time_order
        CHECK (start_time IS NULL OR end_time > start_time),

    CONSTRAINT ck_itinerary_item_data_status
        CHECK (data_status IN ('VERIFIED', 'ESTIMATED', 'UNKNOWN'))
);

CREATE INDEX ix_itinerary_item_version_slot ON itinerary_item (itinerary_version_id, day_index, sequence);
CREATE INDEX ix_itinerary_item_place ON itinerary_item (place_id);
CREATE INDEX ix_itinerary_item_source_request ON itinerary_item (source_request_id);

COMMENT ON COLUMN itinerary_item.itinerary_version_id IS
    '🔴 부모는 itinerary_id 가 아니라 itinerary_version_id 다. itinerary_versions 가 덮어쓰지 않는 스냅샷이기 때문에 항목도 판마다 복사된다 — itinerary_id 에 매달면 새 판을 만들며 항목을 고치는 순간 이전 판이 가리키던 내용이 소급해서 바뀐다.';
COMMENT ON COLUMN itinerary_item.item_key IS
    '판을 건너 같은 항목을 가리키는 열쇠. ItineraryEditController 의 itemId 가 이 값이다. PK 는 판마다 새로 생기므로 이 값이 없으면 "어느 판의 몇 번째 항목이 원래 그 항목인가"를 추적할 수 없다.';
COMMENT ON COLUMN itinerary_item.estimated_cost_krw IS
    'NULL 허용 — place 표에 비용 칸이 없어 아는 값이 아니다. 0 을 넣지 않는다. "공짜"와 "모른다"는 다른 사실이다.';

-- 🔴 막지 못하는 것 — visit_date 가 trip.start_date~end_date 밖이어도 이 CHECK 는 통과한다.
--    표를 건너는 검사(itinerary_item ↔ itinerary_versions ↔ itineraries ↔ trip)는 CHECK
--    제약으로 표현할 수 없다. 응용 계층(ItineraryDraftService)이 여행 기간 안에서만 날짜를
--    배분하는 것으로 지키고, DB 는 이 경계를 대신 지켜주지 않는다 — 그 사실을
--    ItineraryItemConstraintTest 가 명시적으로 통과를 단언해 남긴다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 일정 구간 — 항목 사이의 이동
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE itinerary_leg (
    itinerary_leg_id      UUID          PRIMARY KEY,
    itinerary_version_id  UUID          NOT NULL,

    day_index             INTEGER       NOT NULL,
    sequence              INTEGER       NOT NULL,

    -- NULL 이면 그 날의 첫 구간 — 여행 출발지(trip.origin_lat/lng)에서 출발한다.
    -- Trip(도메인) 주석 그대로: "출발지. 매일 여기서 일정이 시작된다."
    from_place_id         UUID,
    to_place_id           UUID          NOT NULL,

    travel_mode           VARCHAR(30)   NOT NULL,

    distance_m            INTEGER,
    duration_min          INTEGER,
    walking_meters        INTEGER,

    -- 🔴 bigData 의 경사·계단 데이터가 나중에 채울 자리다. 이번 판에서는 그 데이터가
    --    없으므로 전부 NULL 로 남는다 — 지어내지 않는다.
    ascent_m               INTEGER,
    stair_steps            INTEGER,

    created_at             TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_itinerary_leg_version
        FOREIGN KEY (itinerary_version_id) REFERENCES itinerary_versions (itinerary_version_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_leg_from_place
        FOREIGN KEY (from_place_id) REFERENCES place (place_id),
    CONSTRAINT fk_itinerary_leg_to_place
        FOREIGN KEY (to_place_id) REFERENCES place (place_id),

    CONSTRAINT uq_itinerary_leg_slot UNIQUE (itinerary_version_id, day_index, sequence),

    CONSTRAINT ck_itinerary_leg_not_self
        CHECK (from_place_id IS NULL OR from_place_id <> to_place_id),

    -- 🔴 trip.travel_modes 의 ck_trip_travel_modes(V20260903120000)와 같은 아홉 개
    --    목록이어야 한다. 갈라지면 trip 이 받아들인 이동수단을 이 표가 저장을 거부하는
    --    상황이 생긴다.
    CONSTRAINT ck_itinerary_leg_mode
        CHECK (travel_mode IN ('WALK', 'BUS', 'SUBWAY', 'TAXI', 'PRIVATE_CAR',
                               'RENTAL_CAR', 'BICYCLE', 'FERRY', 'OTHER'))
);

CREATE INDEX ix_itinerary_leg_version_slot ON itinerary_leg (itinerary_version_id, day_index, sequence);

COMMENT ON COLUMN itinerary_leg.ascent_m IS
    'bigData 의 경사 데이터가 채울 자리. 이번 판에서는 전부 NULL 이다.';
COMMENT ON COLUMN itinerary_leg.stair_steps IS
    'bigData 의 계단 데이터가 채울 자리. 이번 판에서는 전부 NULL 이다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- itinerary_versions — 어느 추천 요청이 이 판을 만들었는가
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 기존 request_id(VARCHAR(64)) 컬럼을 재활용하지 않는다. 그 컬럼의 COMMENT(V150000)가
--    "recommendation_job 과 조인되지 않는다"고 이미 못 박았고, 실제로 사용자 편집은
--    "req_edit_<uuid>" 형태(UUID 형식이 아니다)를 담는다. 타입을 UUID 로 바꾸면
--    ItineraryEditController 가 만드는 그 값이 저장 시점에 깨진다. 그래서 타입이 맞는
--    칸을 옆에 새로 둔다 — 이 칸은 추천이 실제로 일정을 만들었을 때만 채워진다.
ALTER TABLE itinerary_versions ADD COLUMN source_request_id UUID;

-- 🔴 ON DELETE SET NULL — itinerary_item 의 같은 칸과 같은 이유다. 출처 표시가
--    추천 기록의 삭제를 막으면 안 된다.
-- DEFERRABLE 인 이유는 itinerary_item 의 같은 칸과 같다 — 한 트랜잭션 안에서 일정과
-- 추천 작업이 서로를 가리키는데, 반대편 CHECK 는 지연시킬 수 없어서 이쪽을 미룬다.
ALTER TABLE itinerary_versions ADD CONSTRAINT fk_itinerary_version_source_request
    FOREIGN KEY (source_request_id) REFERENCES recommendation_job (request_id)
    ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

-- 🔴 이 유일 색인의 역할 — 같은 추천 요청이 두 번 실행돼도(워커가 두 번 디스패치되거나
--    손으로 재실행하는 날) 일정 판은 하나만 생긴다. 응용 계층이 먼저 확인하더라도
--    확인과 저장 사이에 다른 실행이 끼어들 수 있으므로(경쟁 조건), 두 번째 INSERT 가
--    여기서 물리적으로 실패하는 것이 마지막 방어선이다. NULL(사용자 편집 판)은 유일성
--    검사에서 빠진다 — 부분 색인(WHERE 절)을 쓰는 이유다.
CREATE UNIQUE INDEX uq_itinerary_version_source_request
    ON itinerary_versions (source_request_id) WHERE source_request_id IS NOT NULL;

-- ══════════════════════════════════════════════════════════════════════════════
-- recommendation_job — 이제 실제로 일정을 가리켜야 한다
-- ══════════════════════════════════════════════════════════════════════════════
-- 🔴 아래 셋에 NOT VALID 를 붙인 이유. 이 표에는 이 기능이 생기기 전에 만들어진 행이
--    이미 들어 있고, 그 행들은 결과 포인터를 가질 수가 없다 — 그때는 일정을 만드는
--    경로 자체가 없었다. NOT VALID 없이 걸면 마이그레이션이 그 행에 걸려 실패하고,
--    Flyway 가 멈추면 컨테이너가 기동하지 못한다. 이 저장소는 2026-09-03 에 정확히
--    그 이유로 운영이 세 번 멈췄다(INC-DEPLOY-001).
--
--    NOT VALID 는 "검사를 안 한다" 가 아니라 "지금 있는 행은 안 본다" 다. 앞으로의
--    INSERT 와 UPDATE 는 전부 검사한다 — 이 제약이 막으려는 것이 바로 그쪽이다.
--    옛 행을 정리한 뒤 VALIDATE CONSTRAINT 로 마저 올리는 것은 별도 마이그레이션의 몫이다.
ALTER TABLE recommendation_job ADD CONSTRAINT fk_recommendation_job_itinerary
    FOREIGN KEY (itinerary_id) REFERENCES itineraries (itinerary_id) NOT VALID;

ALTER TABLE recommendation_job ADD CONSTRAINT ck_recommendation_job_itinerary_version
    CHECK (itinerary_version IS NULL OR itinerary_version >= 1) NOT VALID;

-- 🔴 ck_recommendation_job_versions_present · ck_recommendation_job_snapshots_present
--    (V20260902090000)와 같은 모양이다. ITINERARY_GENERATION 이 SUCCEEDED 로 끝났는데
--    itinerary_id·itinerary_version 이 비어 있으면, 추천은 성공했다고 적혀 있는데 그
--    결과를 담을 곳이 없는 상태다 — recommendation_candidate 만 보면 완벽한 성공으로
--    읽히므로 이 CHECK 가 그 조용한 거짓을 막는다.
ALTER TABLE recommendation_job ADD CONSTRAINT ck_recommendation_job_result_present
    CHECK (job_status <> 'SUCCEEDED' OR job_type <> 'ITINERARY_GENERATION'
           OR (itinerary_id IS NOT NULL AND itinerary_version IS NOT NULL)) NOT VALID;
