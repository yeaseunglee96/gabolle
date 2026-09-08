-- 부산 추천 장소 설문 — 표 두 개
-- S15P21E201-754
--
-- 이 파일은 postgres 컨테이너가 "처음 켜질 때 한 번" 자동으로 돌린다
-- (/docker-entrypoint-initdb.d/ 에 넣어 둔다 — compose.yaml 참고).
-- 🔴 볼륨(survey-pgdata)이 이미 있으면 다시 안 돈다. 표 모양을 고쳤으면
--    ALTER 를 손으로 돌리거나 볼륨을 지워야 반영된다.
--
-- 🔴 이 데이터베이스에 없는 것: IP · 브라우저 · 기기 식별자 · 접속 시각(시:분:초) ·
--    이름 · 연락처 · 이메일 · 학번 · 소속 반. 칸 자체를 안 만든다.
--    칸이 있으면 언젠가 누군가 채운다.

BEGIN;

-- ── 응답 한 건 ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS response (
  id            BIGSERIAL PRIMARY KEY,
  -- 🔴 날짜까지만. 시:분:초를 남기지 않는다.
  --    나이대 + 부산 거주기간 + 정확한 제출 시각을 합치면 좁은 집단에서는
  --    한 사람으로 좁혀진다 (docs/SURVEY-CONSENT.md 5.1 의 "재식별").
  submitted_on  DATE        NOT NULL DEFAULT CURRENT_DATE,

  age_band      TEXT        NOT NULL,
  busan_years   TEXT        NOT NULL,

  -- 동의를 누른 사실만 남긴다. 누가 눌렀는지는 없다 — 남기면 익명이 깨진다.
  consented     BOOLEAN     NOT NULL,

  -- 같은 응답이 두 번 들어왔는지만 본다. 사람을 가리키지 않는다.
  nonce         TEXT        NOT NULL UNIQUE,

  CONSTRAINT response_age_band_ok CHECK (age_band IN (
    'UNDER_20', 'AGE_20_24', 'AGE_25_29', 'AGE_30_34', 'AGE_35_PLUS')),
  CONSTRAINT response_busan_years_ok CHECK (busan_years IN (
    'BORN_HERE', 'OVER_10Y', 'Y_3_10', 'Y_1_3', 'UNDER_1Y', 'NEVER')),
  CONSTRAINT response_consented_ok CHECK (consented IS TRUE)
);

-- ── 그 응답이 추천한 곳 (한 건당 다섯 줄) ─────────────────────────
CREATE TABLE IF NOT EXISTS recommendation (
  id            BIGSERIAL PRIMARY KEY,
  response_id   BIGINT      NOT NULL REFERENCES response(id) ON DELETE CASCADE,

  -- 화면에서 몇 번째로 센 칸인가 (1~5). 유형 순서와는 다르다.
  slot          SMALLINT    NOT NULL,

  -- 🔴 '야간' 과 '축제' 가 이 목록에 없는 것은 실수가 아니다.
  --    야간은 시간이라 when_good 이, 축제는 날짜가 있는 사건이라
  --    limited_time 이 따로 받는다. 유형에 섞으면 "이 사람이 술을 좋아하는지
  --    야경을 좋아하는지" 를 나중에 가를 수 없다.
  place_type    TEXT        NOT NULL,
  place_name    TEXT        NOT NULL,
  when_good     TEXT        NOT NULL,
  limited_time  BOOLEAN     NOT NULL DEFAULT FALSE,

  -- 이 설문의 본체. 여기가 비면 나머지는 그냥 장소 목록이라 쓸 데가 없다.
  reason        TEXT        NOT NULL,

  CONSTRAINT recommendation_slot_ok CHECK (slot BETWEEN 1 AND 5),
  CONSTRAINT recommendation_place_type_ok CHECK (place_type IN (
    'FOOD', 'CAFE', 'NATURE', 'CULTURE', 'MARKET', 'ACTIVITY', 'BAR')),
  CONSTRAINT recommendation_when_good_ok CHECK (when_good IN ('DAY', 'NIGHT', 'ANY')),
  CONSTRAINT recommendation_place_name_ok CHECK (length(btrim(place_name)) BETWEEN 1 AND 60),
  CONSTRAINT recommendation_reason_ok     CHECK (length(btrim(reason))     BETWEEN 1 AND 500),
  -- 한 응답 안에서 같은 유형을 두 번 적을 수 없다 (화면이 유형 카드 하나에 한 곳씩 받는다)
  CONSTRAINT recommendation_one_per_type UNIQUE (response_id, place_type)
);

CREATE INDEX IF NOT EXISTS recommendation_response_idx ON recommendation (response_id);
CREATE INDEX IF NOT EXISTS recommendation_type_idx     ON recommendation (place_type);

COMMIT;
