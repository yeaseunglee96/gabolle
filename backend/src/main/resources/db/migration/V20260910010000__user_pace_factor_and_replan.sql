-- ══════════════════════════════════════════════════════════════════════════════
-- 개인 속도 계수와 남은 하루 재계획 — S15P21E201-96 · -304 · -308
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 두 가지를 더한다.
--   1. user_pace_factor — 이 사람이 계획보다 몇 배 느린가를 담는 표
--   2. ck_itinerary_version_operation 에 REPLAN_DAY 를 더한다
--
-- 한 파일에 둔 이유는 둘이 한 기능의 양쪽이기 때문이다. 계수를 만들어 놓고 그것으로
-- 하루를 다시 짜지 못하면 계수는 아무 데도 안 쓰이고, 재계획만 있고 계수가 없으면
-- 다시 짠 시각이 원래 계획과 똑같다.

-- ══════════════════════════════════════════════════════════════════════════════
-- user_pace_factor — 계획 대비 실제의 배수
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 고치지 않고 판을 새로 만든다. user_taste_vector 와 같은 구조다.
--    과거의 예상 도착 시각이 "어느 계수로" 나왔는지 되짚을 수 있어야 하기 때문이다.
--    같은 행을 덮어쓰면 어제 사용자에게 보여 준 "20분 늦어집니다" 를 오늘의 계수로
--    설명하게 되고, 그건 설명이 아니라 지어내기다.
--
-- 🔴 계수를 못 구한 사람의 행은 아예 만들지 않는다. 1.0 을 적지 않는다.
--    1.0 은 "재 보니 딱 계획대로 다니는 사람이다" 라는 관측이고, 행이 없는 것은
--    "아직 못 재봤다" 는 무지다. 한 번 1.0 으로 적으면 둘을 영영 구분할 수 없고,
--    화면은 "정확히 계획대로 갑니다" 라는 틀린 안심을 그린다. user_taste_weight 가
--    안 물어본 차원에 0 을 안 넣는 것과 같은 이유다.
CREATE TABLE user_pace_factor (
    user_pace_factor_id UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL,

    -- UUID 에는 순서가 없다. 몇 번째 판인지는 이 정수가 답한다.
    version             INTEGER     NOT NULL,

    -- 1.30 = 계획의 1.3배 걸린다(30% 느리다). 1 보다 작으면 계획보다 빠른 사람이다.
    factor              NUMERIC(4, 2) NOT NULL,

    -- 이 계수를 몇 개의 방문 기록으로 만들었나. 🔴 같은 1.30 이라도 3건짜리와
    -- 30건짜리는 다른 값이다 — user_taste_weight.support 와 같은 이유로 함께 남긴다.
    sample_count        INTEGER     NOT NULL,

    -- 🔴 어디까지의 기록을 반영했나. 이 값이 없으면 다음 계산에서 같은 방문을 두 번
    --    세거나 빠뜨린다. ItineraryItemActual.recordedAt 의 최댓값이 들어간다.
    observed_until      TIMESTAMPTZ NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL,

    -- NULL 인 행이 지금 쓰이는 판이다. 자리를 내준 판도 지우지 않는다.
    superseded_at       TIMESTAMPTZ,

    CONSTRAINT fk_user_pace_factor_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,

    CONSTRAINT ck_user_pace_factor_version CHECK (version >= 1),

    -- 0 이하나 음수 배수는 시간이 거꾸로 간다는 뜻이다. 위쪽도 막는다 — 10배는
    -- 사람이 느린 것이 아니라 기록이 잘못된 것이고, 그것을 계수로 받아들이면
    -- 남은 일정 전체가 무의미해진다.
    CONSTRAINT ck_user_pace_factor_range CHECK (factor > 0 AND factor <= 5),

    -- 🔴 표본이 모자라면 행 자체를 안 만든다. 자바 쪽 PaceFactorCalculator.MIN_SAMPLES
    --    와 같은 숫자이고, 그쪽이 먼저 막고 여기가 최종 방어선이다.
    CONSTRAINT ck_user_pace_factor_samples CHECK (sample_count >= 3)
);

-- 한 사람에게 "지금 쓰는 계수" 는 최대 하나다. 애플리케이션의 조심성이 아니라
-- 표가 보장한다 — user_taste_vector 의 uq_user_taste_vector_current 와 같은 방식이다.
CREATE UNIQUE INDEX uq_user_pace_factor_current
    ON user_pace_factor (user_id) WHERE superseded_at IS NULL;

CREATE UNIQUE INDEX uq_user_pace_factor_version
    ON user_pace_factor (user_id, version);

COMMENT ON TABLE user_pace_factor IS
    '계획 시간 대비 실제 시간의 배수. 판 체인이며 superseded_at IS NULL 인 행이 현재 판이다. S15P21E201-304';

-- ══════════════════════════════════════════════════════════════════════════════
-- ck_itinerary_version_operation — REPLAN_DAY 를 더한다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 REGENERATE_DAY 를 재사용하지 않는다. 그 값은 추천 엔진이 그날 방문지를 다시
--    고르는 사건이고, REPLAN_DAY 는 방문지를 그대로 두고 시각만 다시 매기는 사건이다.
--    되돌리기 화면과 최근 변경 목록이 이 값을 읽어 사용자에게 무슨 일이 있었는지
--    말하므로, "장소가 바뀌었다" 와 "시간표가 밀렸다" 를 같은 이름으로 부르면 그
--    설명이 틀린다. ADD_ITEM 이 REPLACE_ITEM 을 재사용하지 않은 것과 같은 판단이다.
--
-- 기존 행은 전부 이 목록 안의 값이므로 NOT VALID 가 필요 없다 — 값을 더하기만 하는
-- 변경이라 위반할 수 있는 기존 행이 없다.
ALTER TABLE itinerary_versions DROP CONSTRAINT ck_itinerary_version_operation;

ALTER TABLE itinerary_versions ADD CONSTRAINT ck_itinerary_version_operation
    CHECK (operation IN ('CREATE', 'REGENERATE', 'REGENERATE_DAY', 'REPLACE_ITEM',
                         'REMOVE_ITEM', 'LOCK_ITEM', 'REORDER', 'REVERT', 'ADD_ITEM',
                         'REPLAN_DAY'));
