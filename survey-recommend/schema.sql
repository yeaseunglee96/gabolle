-- 부산 추천 장소 설문 — 표 두 개
-- S15P21E201-754
--
-- 이 파일은 postgres 컨테이너가 "처음 켜질 때 한 번" 자동으로 돌린다
-- (/docker-entrypoint-initdb.d/ 에 넣어 둔다 — compose.yaml 참고).
-- 🔴 볼륨(survey-pgdata)이 이미 있으면 다시 안 돈다. 표 모양을 고쳤으면
--    ALTER 를 손으로 돌리거나 볼륨을 지워야 반영된다.
--
-- 🔴 이 데이터베이스에 없는 것: IP · 브라우저 · 기기 식별자 · 접속 시각(시:분:초) ·
--    이름 · 이메일 · 학번 · 소속 반. 칸 자체를 안 만든다.
--    칸이 있으면 언젠가 누군가 채운다.
--
-- 🔴 2026-09-09 (S15P21E201-754) 예외 하나가 생겼다: phone.
--    경품 추첨 대상을 나중에 연락하려면 연락처가 있어야 해서, response 한 줄에
--    선택 입력으로 전화번호를 더했다. NULL 이면 그 응답은 추첨에서 빠질 뿐 나머지는
--    그대로 쓴다 — 그래서 이 칸은 NOT NULL 이 아니다.
--
-- 🔴 2026-09-09 (같은 티켓, "가게 이름 자동완성") recommendation 에 place_id ·
--    gu 두 칸이 더 생겼다. 응답자가 우리 목록(부산 음식점 53,716곳 색인,
--    survey-recommend/places.json — build-places.mjs 가 만든다)에서 가게를
--    직접 고르면 채워지고, 「목록에 없어요 — 직접 적기」로 낸 응답은 여전히
--    NULL 이다 — place_name 은 어느 쪽이든 사람이 본 이름 그대로 남는다.
--    place_id 는 데이터베이스가 아니라 색인 파일을 가리키므로 여기엔
--    REFERENCES(외래키)를 걸지 않는다 — server.mjs 가 제출 시점에 색인에
--    실제로 있는 id 인지 확인한 뒤에만 넣는다.
--    🔴 이미 떠 있는 서버의 표는 이 파일이 다시 안 돈다(위 6번 줄). 그쪽은
--    survey-recommend/migrations/0001_add_place_id.sql 을 손으로 돌려야 한다.

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

  -- 🔴 경품 추첨용. 반드시 선택(NULL 허용) — 경품을 원치 않는 사람의 응답까지
  --    막으면 안 된다. 저장은 숫자만 (하이픈 없이) — index.html · server.mjs 가
  --    같은 규칙(01[016789] 로 시작, 총 10~11자리)으로 정규화한 뒤 넣는다.
  --    추첨이 끝나면 이 칸만 NULL 로 지운다 (응답 자체는 남긴다).
  phone         TEXT,

  -- 동의를 누른 사실만 남긴다. 누가 눌렀는지는 없다 — 남기면 익명이 깨진다.
  consented     BOOLEAN     NOT NULL,

  -- 같은 응답이 두 번 들어왔는지만 본다. 사람을 가리키지 않는다.
  nonce         TEXT        NOT NULL UNIQUE,

  -- 🔴 2026-09-09 (S15P21E201-754, 팀원 피드백) 다섯 칸 → 세 칸으로 바꿨다.
  --    UNDER_20 · AGE_20_24 · AGE_25_29 · AGE_30_34 · AGE_35_PLUS 를 버리고
  --    AGE_20_39 · AGE_40_59 · AGE_60_79 로 다시 나눴다. DB 가 그때까지
  --    0행이라 값 이관 없이 그대로 바꿨다.
  --    🔴 빈 구멍: 20세 미만 · 80세 이상은 이 세 칸에 안 들어간다. age_band 가
  --    NOT NULL(필수)이라 그 나이의 응답자는 이 표에 줄을 못 남긴다 — 메우라는
  --    지시가 없어 그대로 뒀다 (index.html 의 AGE 배열 주석 참고).
  CONSTRAINT response_age_band_ok CHECK (age_band IN (
    'AGE_20_39', 'AGE_40_59', 'AGE_60_79')),
  -- 🔴 2026-09-09 (같은 티켓) 'NEVER'(부산에 산 적 없음) → 'VISITED_ONLY'
  --    (부산 여행 경험 있음)로 이름을 바꿨고, 구간 경계도 10/3/1년 → 20/10/5년
  --    으로 넓혔다: OVER_10Y→OVER_20Y · Y_3_10→Y_10_20 · Y_1_3→Y_5_10 ·
  --    UNDER_1Y→UNDER_5Y. BORN_HERE 는 그대로다. DB 가 그때까지 0행이라
  --    값 이관 없이 그대로 바꿨다.
  CONSTRAINT response_busan_years_ok CHECK (busan_years IN (
    'BORN_HERE', 'OVER_20Y', 'Y_10_20', 'Y_5_10', 'UNDER_5Y', 'VISITED_ONLY')),
  CONSTRAINT response_consented_ok CHECK (consented IS TRUE),
  CONSTRAINT response_phone_ok CHECK (phone IS NULL OR phone ~ '^01[016789][0-9]{7,8}$')
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

  -- 🔴 2026-09-09 (S15P21E201-754) 목록에서 고른 경우만 채워진다. 색인
  --    파일(우리 데이터, 카카오·네이버 지도가 아니다)의 상가업소번호다.
  --    NULL 허용 — 「목록에 없어요 — 직접 적기」로 낸 응답은 이름만 있다.
  place_id      TEXT,
  -- 그 가게가 속한 구·군. place_id 가 있을 때만 서버가 색인에서 찾아 채운다
  -- (클라이언트가 보낸 값을 안 믿는다). NULL 허용.
  gu            TEXT,

  CONSTRAINT recommendation_slot_ok CHECK (slot BETWEEN 1 AND 5),
  CONSTRAINT recommendation_place_type_ok CHECK (place_type IN (
    'FOOD', 'CAFE', 'NATURE', 'CULTURE', 'MARKET', 'ACTIVITY', 'BAR')),
  CONSTRAINT recommendation_when_good_ok CHECK (when_good IN ('DAY', 'NIGHT', 'ANY')),
  CONSTRAINT recommendation_place_name_ok CHECK (length(btrim(place_name)) BETWEEN 1 AND 60),
  CONSTRAINT recommendation_reason_ok     CHECK (length(btrim(reason))     BETWEEN 1 AND 500),
  -- 한 응답 안에서 칸 번호는 겹치지 않는다 (1~5 가 한 번씩)
  CONSTRAINT recommendation_one_per_slot UNIQUE (response_id, slot)

  -- 🔴 UNIQUE (response_id, place_type) 을 두지 않는다. 일부러 뺀 것이다.
  --    한 응답이 같은 유형을 다섯 번 적을 수 있어야 한다 — 맛집을 다섯 곳
  --    아는 사람의 답이 우리가 가장 원하는 답인데, 유형당 하나로 막으면
  --    그 답이 네 곳 잘려나간다.
  --    (2026-09-08 까지는 그 제약이 있었다. 걷어냈고, 되살리지 않는다.)
);

CREATE INDEX IF NOT EXISTS recommendation_response_idx ON recommendation (response_id);
CREATE INDEX IF NOT EXISTS recommendation_type_idx     ON recommendation (place_type);

COMMIT;
