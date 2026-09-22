-- 여행 기분(pace) — 하루에 몇 곳을 넣을지를 정한다.
--
-- 앱은 이 답을 온보딩 5번에서 받아 왔고 화면에 「하루 2–3곳」처럼 곳 수까지 적어 두는데,
-- 서버로 오는 길이 없어서 일정은 전원 똑같이 설정 기본값(4곳)으로 만들어졌다.
--
-- 취향이 아니라 「여행의 모양」이라 preference_answer 가 아니라 trip 에 둔다. travel_modes 와
-- 같은 자리다 — preference_answer.dimension 의 CHECK 어휘에도 일부러 넣지 않는다.
ALTER TABLE trip
    ADD COLUMN IF NOT EXISTS pace VARCHAR(16);

-- 아는 값만 받는다. NULL 은 「안 골랐다」이고 그때 일정 생성은 지금까지처럼 설정 기본값을 쓴다 —
-- 「보통(BALANCED)」으로 채우지 않는다. 안 고른 것과 보통을 고른 것은 다른 사실이다.
ALTER TABLE trip
    DROP CONSTRAINT IF EXISTS ck_trip_pace;

ALTER TABLE trip
    ADD CONSTRAINT ck_trip_pace
    CHECK (pace IS NULL OR pace IN ('RELAXED', 'BALANCED', 'PACKED'));

COMMENT ON COLUMN trip.pace IS
    '여행 기분 — RELAXED/BALANCED/PACKED. 하루 곳 수를 정한다. NULL 은 안 고른 것이다.';
