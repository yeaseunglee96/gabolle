-- S15P21E201-554 — 제약 값 규칙을 고치고, 버려지던 여행 시간대 입력을 받아 둔다.
--
-- 둘 다 S15P21E201-461 JPA 매핑을 막고 있던 것이다. 모진성 님이 엔티티를 만들다 찾았다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. 🔴 제 결함이다 — ALLERGY·DIET 에 의미 없는 JSON 을 억지로 넣게 만들고 있었다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 원래 CHECK 는 이랬다.
--
--     CHECK ((answer_status = 'SELECTED') = (value IS NOT NULL))
--
-- "고른 답에는 값이 반드시 있다" 는 뜻인데, 종류마다 사정이 다르다는 것을 못 봤다.
--
--   MOBILITY / MAX_WALKING_METERS : {"meters": 1500}  ← 값이 진짜 정보다
--   MOBILITY / WHEELCHAIR         : 없음. 키 자체가 정보다
--   ALLERGY  / PEANUT             : 없음. 코드가 정보 전부다
--   DIET     / HALAL              : 없음. 필요한 것은 diet_requirement 칸에 있다
--
-- 🔴 그래서 ALLERGY·DIET 를 저장하려면 아무 JSON 이나 채워 넣어야 했다. 실제로 그렇게
--    되고 있었다 — -461 엔티티가 코드를 value 에 넣고 있었고, 그건 그쪽 실수가 아니라
--    이 CHECK 가 강요한 것이다. 제 테스트조차 {"severity":"HARD"} 를 넣었는데 그건
--    hard 칸과 중복이다.
--
-- 억지로 채운 값은 나중에 "이게 진짜 데이터인가 자리 채우기인가" 를 구별할 수 없게 만든다.
-- 값을 요구하는 자리와 안 요구하는 자리를 나눈다.

ALTER TABLE constraint_answer
    DROP CONSTRAINT ck_constraint_answer_value_matches_status;

ALTER TABLE constraint_answer
    -- 안 고른 답(NONE · UNKNOWN)은 여전히 값을 실을 수 없다. 이쪽이 원래 막으려던 것이고
    -- 그대로 둔다 — 건너뜀·모름에 0 이나 빈 객체가 새어 들어오는 것을 막는다.
    ADD CONSTRAINT ck_constraint_answer_value_absent_unless_selected
        CHECK (answer_status = 'SELECTED' OR value IS NULL),

    -- 값이 정보를 담는 종류만 값을 요구한다. 지금은 MOBILITY 뿐이다.
    -- 🔴 ALLERGY·DIET 는 value 를 NULL 로 둔다. 코드는 constraint_key 에 있다.
    ADD CONSTRAINT ck_constraint_answer_mobility_value_present
        CHECK (constraint_type <> 'MOBILITY'
               OR answer_status <> 'SELECTED'
               OR value IS NOT NULL);

COMMENT ON COLUMN constraint_answer.value IS
    '종류마다 값이 있는 것도 없는 것도 정상이다. MOBILITY 는 SELECTED 면 반드시 있어야 하고(예: {"meters":1500}), ALLERGY·DIET 는 코드가 constraint_key 에 있으므로 대개 NULL 이다. 🔴 자리를 채우려고 아무 JSON 이나 넣지 않는다 — 나중에 진짜 데이터와 구별할 수 없게 된다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. 🔴 여행 가능 시간대가 조용히 버려지고 있었다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 화면·도메인은 "MORNING_TO_EVENING" 같은 프리셋 하나를 준다. 표에는 시각 두 칸
-- (time_window_start · time_window_end) 만 있고, 프리셋을 시각으로 바꾸는 규칙이
-- 어디에도 없다. 그래서 그 값이 저장되지 않고 사라지고 있었다.
--
-- 🔴 프리셋 목록과 각각의 시간 범위는 여기서 정하지 않는다. 명세 4.2 에 그 목록이 없고,
--    화면이 무엇을 주는지가 계약이라 BE·FE·DATA 3자 사안이다 (S15P21E201-458·459·460).
--    지금 넷쯤 지어 넣으면 그것이 계약이 되어 버린다.
--
-- 대신 원본을 받아 둔다. -542 2.3 — "수집 시 즉시 변환하지 않는다."
-- 프리셋이 사용자가 실제로 고른 것이고, 시각 두 칸은 그것에서 나오는 파생값이다.
--
-- 🔴 지금 버리면 되돌릴 수 없다. "그 사용자가 언제 다닐 수 있다고 했는가" 는 나중에
--    재구성할 방법이 없다. answer_status 와 같은 종류의 손실이다.
ALTER TABLE trip
    ADD COLUMN time_window_preset VARCHAR(40);

COMMENT ON COLUMN trip.time_window_preset IS
    '사용자가 고른 시간대 프리셋 원본(예: MORNING_TO_EVENING). 🔴 변환하지 말고 그대로 넣는다. time_window_start/end 는 이것에서 나오는 파생값이고, 프리셋 목록이 확정되면(BE·FE·DATA 3자) 그때 채운다. 값 목록에 CHECK 를 걸지 않은 이유도 같다 — 확정 전에 박으면 그것이 계약이 된다.';
