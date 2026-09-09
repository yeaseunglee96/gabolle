-- 로컬 탐색 묶음 (S15P21E201-465 · -469 · -473 · -476 · -217 · -430)
--
-- 프론트의 다섯 화면(축제 골라 보기 · 근처 기념품샵 · 로컬 8갈래 아코디언 · 택시 목적지 카드 ·
-- 장소 상세)이 필요로 하는 칸을 한 번에 만든다. 조회 API 는 있는데 담을 곳이 없어서 못 주고
-- 있던 것들이다.
--
-- 🔴 번호를 날짜로 지어내지 않았다. 2026-09-07 에 그렇게 해서 운영이 죽었다(INC-DEPLOY-003).
--    처음 만들 때 `ls db/migration | sort | tail -1` 이 V20260907140000 이고 운영
--    flyway_schema_history 의 최고 적용 번호도 20260907140000 이어서 150000 을 썼다.
--
--    🔴 **그런데 150000 을 다른 사람이 먼저 머지했다** — 고지혁 님의
--    V20260907150000__editorial_pick_and_source_mode.sql. 쪽지로 알려 주셔서 160000 으로
--    옮겼다. 즉 "머지 시점에 가장 큰 번호" 를 확인해야 하고, **작업을 시작한 시점의 최대값은
--    답이 아니다.** 하루에 티켓 여섯~일곱이 동시에 도는 상황에서 그 사이에 남이 하나 넣는다.
--    이번엔 사람이 잡았지만 CI 잡이 이 대조를 하도록 이예승 님께 제안해 뒀다.

-- ── 1. 영문 주소와 대표 사진 ──────────────────────────────────────────────────
--
-- 택시 카드(-217)는 기사에게 한국어 주소를 보여주고 외국인 승객에게는 영문 주소를 보여줘야
-- 한다. 지금 place 에는 주소가 한 칸뿐이다.
--
-- 🔴 사진은 칸만 만든다. 채우는 것은 외부 사진 검색(-146 · -480)이고 그건 업체와 열쇠가
--    정해져야 한다. 값이 없으면 응답에서 그 칸 자체를 빼기로 FE 와 합의했다 — 빈 문자열이나
--    null 을 보내면 화면이 "사진이 있는데 못 불러왔다" 와 구분할 수 없다.
ALTER TABLE place
    ADD COLUMN address_en   VARCHAR(300),
    ADD COLUMN photo_url    VARCHAR(500),
    ADD COLUMN photo_source VARCHAR(100);

COMMENT ON COLUMN place.address_en IS
    '영문 주소. 택시 목적지 카드(-217)와 언어별 응답(-430)이 쓴다. 없으면 응답에서 칸 자체를 뺀다.';
COMMENT ON COLUMN place.photo_url IS
    '대표 사진 주소. 🔴 지금은 채우는 경로가 없다 — 외부 사진 검색(-146 · -480)이 붙기 전까지 항상 NULL 이다.';
COMMENT ON COLUMN place.photo_source IS
    '그 사진의 출처 표기 문구. 저작권 표기 없이 남의 사진을 쓰지 않기 위해 URL 과 짝으로 둔다.';

-- ── 2. 축제 기간 ─────────────────────────────────────────────────────────────
--
-- 🔴 place_feature 에 넣지 않고 표를 따로 만든다. 이유가 둘이다.
--
--    첫째, place_feature 는 "이 장소가 어떤 성질인가" 를 담는 곳이다(조용한가, 로컬 비율이
--    얼마인가). 기간은 성질이 아니라 별도의 사실이고, 같은 축제가 해마다 다른 기간으로
--    여러 번 열린다 — place_feature 는 (place_id, feature_type) 이 UNIQUE 라 여러 회차를
--    담을 수 없다.
--
--    둘째, 겹침 판정을 SQL 로 해야 한다. place_feature 의 값은 JSONB 라 날짜 비교에
--    인덱스를 걸기 어렵다. 여행 기간과 겹치는 축제만 고르는 것이 이 표의 유일한 용도이므로
--    (start_date, end_date) 에 인덱스를 거는 형태가 맞다.
CREATE TABLE place_event_period (
    place_event_period_id UUID        PRIMARY KEY,
    place_id              UUID        NOT NULL,
    -- 회차 이름. "2026 진주 남강유등축제" 처럼 해마다 달라지는 부분을 담는다. 없으면 장소 이름을 쓴다
    title                 VARCHAR(200),
    start_date            DATE        NOT NULL,
    end_date              DATE        NOT NULL,
    -- 출처는 장소 행과 따로 남긴다. 장소는 카카오에서 왔는데 기간은 지자체 공고에서 올 수 있다
    source_type           VARCHAR(50),
    source_id             VARCHAR(200),
    observed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_place_event_period_place
        FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE CASCADE,
    -- 🔴 하루짜리 축제가 있으므로 같은 날도 허용한다 (< 가 아니라 <=)
    CONSTRAINT ck_place_event_period_range
        CHECK (end_date >= start_date),
    -- 같은 장소에 같은 기간을 두 번 적재하는 것을 막는다. 적재가 여러 번 돌아도 안전하다
    CONSTRAINT uq_place_event_period
        UNIQUE (place_id, start_date, end_date)
);

COMMENT ON TABLE place_event_period IS
    '축제·행사가 실제로 열리는 기간 (S15P21E201-465). 한 장소에 여러 회차가 있을 수 있다. 겹침 판정은 서버가 한다 — 화면이 거르지 않는다.';

-- 겹침 질의는 `start_date <= :여행종료 AND end_date >= :여행시작` 형태다. 두 칸을 함께 훑으므로
-- 복합 인덱스를 건다
CREATE INDEX ix_place_event_period_range
    ON place_event_period (start_date, end_date);
CREATE INDEX ix_place_event_period_place
    ON place_event_period (place_id);

-- ── 3. 영업시간·예상비용을 담을 표식 종류 ──────────────────────────────────────
--
-- 장소 상세(-476)가 영업시간과 예상비용을 줘야 하는데 place_feature 의 CHECK 가 14종만
-- 허용하고 있어 넣을 수 없었다.
--
-- 🔴 이 둘은 place_feature 가 맞다. 성질이고, evidence_status(VERIFIED · ESTIMATED ·
--    UNKNOWN)가 실제로 의미가 있다 — "예상비용" 은 대부분 ESTIMATED 이고 영업시간은 수집
--    시점에 따라 낡는다. 화면이 "확인된 값" 과 "추정값" 을 다르게 보여줄 수 있어야 한다.
--
-- 둘 다 feature_key 를 쓰지 않는다(점수형·값형과 같은 모양). 값은 value JSONB 에 담는다 —
-- 영업시간은 요일별 구조라 한 칸에 안 들어가고, 비용은 통화와 범위가 함께 와야 한다.
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_type;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_type
        CHECK (feature_type IN (
            'INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
            'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
            'LOCALITY_SCORE', 'QUIETNESS_SCORE', 'TOURIST_RATIO',
            'POPULARITY_SCORE', 'CROWDING_SCORE', 'SHADE_SCORE', 'SLOPE_PERCENT',
            'STAIRS_PRESENT',
            -- 여기서부터 이번에 더한 둘
            'OPENING_HOURS', 'PRICE_LEVEL'));

COMMENT ON COLUMN place_feature.feature_type IS
    '명세 6.2 의 피처 14종 + 영업시간·예상비용(S15P21E201-476). 태그형은 feature_key 에 코드가 오고 나머지는 키가 없다. 🔴 안쪽 코드값에는 CHECK 를 걸지 않는다 — 화면 옵션과 온톨로지가 확정되면 같은 코드로 고정한다.';

-- ── 4. 일정 편집 종류에 '장소 더하기' 를 더한다 ────────────────────────────────
--
-- 축제를 일정에 넣는 경로(S15P21E201-467)가 새 판을 만들 때 그 판의 operation 이 필요하다.
-- 지금 CHECK 는 여덟 종류만 허용해서 넣을 수 있는 값이 없다.
--
-- 🔴 REPLACE_ITEM 을 재사용하지 않는다. 교체는 "있던 것을 다른 것으로 바꿨다" 이고 더하기는
--    "없던 것이 생겼다" 다. 되돌리기 화면과 최근 변경 목록이 이 값을 읽어 사용자에게 무슨 일이
--    있었는지 말하므로, 두 사건을 같은 이름으로 부르면 그 설명이 틀린다.
--
-- 기존 행은 전부 이 목록 안의 값이므로 NOT VALID 가 필요 없다 — V20260906140000 이 REVERT 를
-- 더할 때와 같은 상황이다.
ALTER TABLE itinerary_versions DROP CONSTRAINT ck_itinerary_version_operation;

ALTER TABLE itinerary_versions ADD CONSTRAINT ck_itinerary_version_operation
    CHECK (operation IN ('CREATE', 'REGENERATE', 'REGENERATE_DAY', 'REPLACE_ITEM',
                         'REMOVE_ITEM', 'LOCK_ITEM', 'REORDER', 'REVERT', 'ADD_ITEM'));
