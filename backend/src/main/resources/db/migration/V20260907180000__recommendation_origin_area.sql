-- S15P21E201-550 — 현재 위치 추천의 파생값만 남긴다.
--
-- 목적은 하나다. 현재 위치로 추천을 해 주면서 **정확한 좌표를 영구 행동 이력으로
-- 남기지 않는다** (FR-ACC-05 · FR-REC-10 · S-12).
--
-- ── 🔴 이 마이그레이션이 하지 않는 것 ───────────────────────────────────────
--
-- **좌표 칸을 만들지 않는다.** origin_lat / origin_lng 같은 칸은 여기 없고 앞으로도
-- 안 만든다. 요청이 받은 좌표는 거리·이동시간 계산에만 쓰이고 요청이 끝나면 사라진다
-- (RequestLocation javadoc).
--
-- 그래서 -550 의 완료 기준 셋째 "정확 좌표 보존 기간을 자동 검사할 수 있다" 가
-- **보존 기간이 0 이라서** 충족된다. 지울 것을 주기적으로 관리하는 대신 넣을 자리를
-- 아예 안 만들었다 — 관리해야 하는 규칙은 언젠가 안 지켜지고, 없는 칸은 안 지켜질 수
-- 없다.
--
-- 검사는 RequestLocationPrivacyTest 가 한다. 저장되는 JSONB 와 이 표의 칸들을 훑어
-- 좌표로 보이는 값이 없는지 본다.
--
-- ── 무엇을 남기나 ───────────────────────────────────────────────────────────
--
--   origin_area_code   대략 1km 칸 ("3515:12905"). CoarseArea 가 만든다
--   origin_source      GPS | MANUAL | TRIP_ORIGIN
--
-- 후보별 거리(distanceM)와 거리 띠(distanceBucket)는 이미 있는
-- recommendation_candidate.feature_values 에 들어간다 — 칸을 늘리지 않는다.

ALTER TABLE recommendation_job
    ADD COLUMN origin_area_code VARCHAR(32),
    ADD COLUMN origin_source    VARCHAR(20);

-- 🔴 값 목록을 스키마에서 막는다. 애플리케이션 열거형과 갈리면 저장은 되는데 집계가
--    조용히 갈라진다 — "MANUAL" 과 "manual" 이 다른 지역처럼 세어지는 식이다.
ALTER TABLE recommendation_job
    ADD CONSTRAINT ck_recommendation_job_origin_source
        CHECK (origin_source IS NULL
               OR origin_source IN ('GPS', 'MANUAL', 'TRIP_ORIGIN'));

-- 🔴 칸 번호가 좌표처럼 보이지 않게 정수 쌍만 받는다. 소수점이 들어오면 그것은
--    CoarseArea 를 안 지나온 값이고, 즉 어딘가에서 정밀 좌표가 새어 들어온 것이다.
--    그 순간 거부하는 것이 나중에 찾는 것보다 싸다.
ALTER TABLE recommendation_job
    ADD CONSTRAINT ck_recommendation_job_origin_area_shape
        CHECK (origin_area_code IS NULL
               OR origin_area_code ~ '^-?[0-9]+:-?[0-9]+$');

-- 분석 질의가 쓰는 길: 어느 지역에서 온 요청이 몇 건인가.
CREATE INDEX ix_recommendation_job_origin_area
    ON recommendation_job (origin_area_code, created_at);

COMMENT ON COLUMN recommendation_job.origin_area_code IS
    'S15P21E201-550 — 출발지를 대략 1km 칸으로 뭉갠 번호("3515:12905"). 🔴 정밀 좌표는 어디에도 저장하지 않는다 — 이 서비스에는 그 칸이 없다. 되돌려도 이 칸보다 정밀한 위치가 나오지 않는다.';
COMMENT ON COLUMN recommendation_job.origin_source IS
    'S15P21E201-550 — GPS | MANUAL | TRIP_ORIGIN. 🔴 MANUAL 은 열등한 경로가 아니다: 위치 권한을 거부한 사용자의 1급 fallback 이고 추천 품질이 다르지 않다.';
