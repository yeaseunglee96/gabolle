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
--
-- 🔴 2026-09-10 (같은 티켓) response 에 개인화 세 문항(spend_profile ·
--    spend_profile_status)과 설문 판 번호(form_version)가, 그리고 짝 비교
--    (pairwise_status · pairwise_design_id · pairwise_trap_passed + 새 표
--    pairwise_choice + 뷰 pairwise_choice_real)가 더 생겼다.
--    🔴 이미 떠 있는 서버는 이 파일이 다시 안 돈다. 그쪽은
--    migrations/0002_add_spend_profile.sql 과 0003_add_pairwise.sql 을
--    **순서대로** 손으로 돌려야 한다. 이 파일과 그 둘은 **같은 결과**를
--    내야 한다 — 칸 이름 · 제약 이름 · 제약 내용을 한 글자도 다르지 않게 맞춘다.
--    (한쪽만 고치면 새로 띄운 DB 와 살아 있는 DB 가 서로 다른 표가 되고,
--     그건 두 DB 의 응답을 합치려는 날에야 드러난다.)
--
-- 🔴 2026-09-10 (같은 티켓) 네 번째 개인화 문항 "북적임" 이 붙었다 —
--    response 에 crowd_pref · crowd_pref_status 두 칸. 살아 있는 DB 는
--    migrations/0004_add_crowd.sql 을 0003 **뒤에** 돌린다.
--    🔴 앱 온보딩은 여전히 세 질문이다. 넷은 이 설문에서만이고, 이유는
--    docs/COLDSTART-THREE-QUESTIONS.md 2.6 에 적어 뒀다 (이탈 비용이 다르다).
--
-- 🔴 2026-09-10 (같은 티켓) 추천 장소 칸이 **다섯에서 열까지** 늘 수 있게 됐다 —
--    recommendation_slot_ok 의 상한만 5 → 10. 살아 있는 DB 는
--    migrations/0005_more_slots.sql 을 0004 **뒤에** 돌린다.
--    🔴 기본은 여전히 다섯이다. 화면의 「한 곳 더 적기」를 누른 사람만 는다.
--
-- 🔴 2026-09-10 (같은 티켓) 장소 유형이 **일곱에서 여덟**이 됐다 —
--    「관광 · 명소 · 경치」(SIGHT). recommendation_place_type_ok 만 넓어진다.
--    살아 있는 DB 는 migrations/0006_add_sight_type.sql 을 0005 **뒤에** 돌린다.
--    🔴 왜 하나 더 필요했는지(해운대해수욕장 · 감천문화마을이 NATURE 와
--       CULTURE 사이에서 갈렸다)는 그 파일과 README.md 2절에 적어 뒀다.
--    🔴 옛 행의 유형을 다시 나누지 않는다. 무엇을 보고 그렇게 적었는지 우리가
--       모르기 때문이다 — 가르는 기준은 form_version(6 이상이면 SIGHT 가 있던 판)이다.
--
-- 🔴 새로 더한 칸은 전부 NULL 허용이다. NULL 이 곧 "그 판에서는 안 물어봤음"
--    이다. 이미 응답이 들어 있는 표에 NOT NULL 을 걸면 ALTER 가 거부되고
--    살아 있는 설문이 그 자리에서 멈춘다.

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

  -- ── 개인화 세 문항 (오는 교통 · 숙소 · 식사) — migrations/0002 와 같은 것 ──
  -- 세 문항을 세 칸으로 쪼개지 않았다. 문항을 하나 늘릴 때마다 고쳐야 하는
  -- 곳이 세 배가 되고, 그러다 한 곳을 빠뜨리면 조용히 깨진다.
  -- 🔴 답한 키만 들어간다. 건너뛴 키는 null 이 아니라 아예 없다.
  spend_profile        JSONB,
  -- NULL = 안 물어봤음 · 'SELECTED' = 하나 이상 골랐음 · 'SKIPPED' = 봤는데 안 골랐음.
  -- 값이 비어 있는 이유가 셋이라 값만으로는 못 가린다.
  spend_profile_status TEXT,

  -- 🔴 이 응답이 어느 판의 설문에 답했나.
  --      NULL 또는 1 = 이 칸이 생기기 전 판 (추천 다섯 곳 · 전화번호 · 가게 자동완성)
  --      2           = 개인화 세 문항이 붙은 판
  --      3           = 짝 비교까지 붙은 판
  --      4           = 북적임 문항까지 붙은 판 (migrations/0004)
  --      5           = 장소 칸을 열까지 늘릴 수 있는 판 (migrations/0005)
  --    없으면 나중에 반드시 이렇게 잘못 읽는다: "응답 100건 중 짝 비교가
  --    60건뿐이네 → 응답률 60%". 사실은 40건이 그 문항이 생기기 **전에**
  --    들어온 것이라 응답률은 100% 다.
  form_version         SMALLINT DEFAULT 1,

  -- ── 짝 비교 여섯 문항 (화면 3장 × 2문항) — migrations/0003 과 같은 것 ──
  -- NULL = 안 물어봤음 · 'ANSWERED' = 여섯 문항을 다 답했음.
  -- 🔴 'SKIPPED' 가 없다. 짝 비교 화면에는 건너뛰기가 없기 때문이다 —
  --    강제 선택이 짝 비교의 본체라, 건너뛸 수 있으면 배수를 못 잰다.
  pairwise_status      TEXT,
  pairwise_design_id   TEXT,
  -- 함정 문항을 통과했나. pairwise_choice 의 함정 줄에서 나오는 값이지만,
  -- 응답 단위로 거르는 일이 잦아 여기에도 둔다. 둘 다 server.mjs 가
  -- 같은 트랜잭션에서 같은 문항 정의를 보고 적는다.
  pairwise_trap_passed BOOLEAN,

  -- ── 네 번째 개인화 문항: 북적임 — migrations/0004 와 같은 것 ────────
  -- 🔴 칸 순서를 맨 뒤로 둔다. 마이그레이션은 ALTER … ADD COLUMN 이라
  --    반드시 맨 뒤에 붙는다. 여기서 가운데에 끼우면 새로 띄운 DB 와 살아
  --    있는 DB 의 칸 순서(ordinal_position)가 갈라진다.
  -- 🔴 spend_profile JSONB 에 키를 하나 더 넣지 않은 이유는 0004 에 적혀 있다 —
  --    북적임은 돈이 아니라 밀도라 다른 차원이고, 상태 칸을 두 차원이 나눠
  --    쓰면 "북적임만 답한 사람" 이 지불 의사 'SELECTED' 로 남는다.
  -- NULL = 안 물어봤음 또는 건너뜀 (어느 쪽인지는 아래 상태 칸이 가른다).
  crowd_pref           TEXT,
  -- NULL = 안 물어봤음 · 'SELECTED' = 골랐음 · 'SKIPPED' = 봤는데 안 골랐음.
  crowd_pref_status    TEXT,

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
  CONSTRAINT response_phone_ok CHECK (phone IS NULL OR phone ~ '^01[016789][0-9]{7,8}$'),

  -- 🔴 아래 다섯 제약은 migrations/0002 · 0003 이 ALTER 로 더하는 것과
  --    이름도 내용도 같아야 한다.
  CONSTRAINT response_spend_profile_status_ok
    CHECK (spend_profile_status IS NULL OR spend_profile_status IN ('SELECTED', 'SKIPPED')),
  -- 고른 답에는 값이 반드시 있고, 건너뜀·안 물어봄에는 값을 실을 수 없다
  CONSTRAINT ck_spend_profile_value_matches_status CHECK (
    -- 🔴 CASE 로 쓴다. OR 로 늘어놓으면 상태가 NULL 일 때 각 가지가
    --    TRUE/FALSE 가 아니라 **NULL** 이 되고, CHECK 는 NULL 을 통과시킨다
    --    (SQL 의 3값 논리 — 거짓이 아니라 "모른다" 라서 안 막는다).
    --    실제로 그렇게 안 막히는 것을 확인하고 고쳤다.
    CASE WHEN spend_profile_status = 'SELECTED'
         THEN spend_profile IS NOT NULL
              AND jsonb_typeof(spend_profile) = 'object'
              AND spend_profile <> '{}'::jsonb
         ELSE spend_profile IS NULL
    END),
  -- 바깥 껍데기(키 이름)는 여기서 막고, 안쪽 코드값은 server.mjs 가 막는다
  CONSTRAINT ck_spend_profile_keys_known CHECK (
    spend_profile IS NULL
    OR spend_profile - ARRAY['transport', 'stay', 'meal'] = '{}'::jsonb),
  CONSTRAINT response_form_version_ok CHECK (form_version IS NULL OR form_version >= 1),
  CONSTRAINT response_pairwise_status_ok
    CHECK (pairwise_status IS NULL OR pairwise_status = 'ANSWERED'),
  CONSTRAINT ck_pairwise_value_matches_status CHECK (
    -- 🔴 여기도 CASE 다 (위 ck_spend_profile_value_matches_status 와 같은 이유).
    CASE WHEN pairwise_status = 'ANSWERED'
         THEN pairwise_design_id IS NOT NULL
              AND length(btrim(pairwise_design_id)) BETWEEN 1 AND 60
              AND pairwise_trap_passed IS NOT NULL
         ELSE pairwise_design_id IS NULL AND pairwise_trap_passed IS NULL
    END),

  -- 🔴 아래 셋은 migrations/0004 가 ALTER 로 더하는 것과 이름도 내용도 같아야 한다.
  CONSTRAINT response_crowd_pref_status_ok
    CHECK (crowd_pref_status IS NULL OR crowd_pref_status IN ('SELECTED', 'SKIPPED')),
  -- 🔴 이 칸은 JSONB 가 아니라 글자 한 칸이라 코드값 목록을 DB 가 직접 막는다
  --    (age_band · busan_years 와 같은 방식). 목록이 server.mjs 의
  --    CROWD_CHOICES 와 두 벌이니 고칠 때 반드시 같이 고친다.
  --    🔴 CROWD_VARIES("그날그날 달라요")는 눈금 위의 한 점이 아니고 건너뛴
  --       것도 아니다 — "밀도를 고정하지 않는 사람" 이라는 답이라 저장은 하고
  --       계산에서만 뺀다. 식사 문항의 'VARIES' 와 같은 취급이다.
  CONSTRAINT response_crowd_pref_ok CHECK (crowd_pref IS NULL OR crowd_pref IN (
    'CROWD_BUSY', 'CROWD_EDGE', 'CROWD_QUIET', 'CROWD_VARIES')),
  CONSTRAINT ck_crowd_pref_value_matches_status CHECK (
    -- 🔴 여기도 CASE 다 (위 둘과 같은 이유).
    CASE WHEN crowd_pref_status = 'SELECTED'
         THEN crowd_pref IS NOT NULL
         ELSE crowd_pref IS NULL
    END)
);

-- ── 그 응답이 추천한 곳 (한 건당 다섯 줄, 최대 열 줄) ─────────────
CREATE TABLE IF NOT EXISTS recommendation (
  id            BIGSERIAL PRIMARY KEY,
  response_id   BIGINT      NOT NULL REFERENCES response(id) ON DELETE CASCADE,

  -- 화면에서 몇 번째로 센 칸인가 (1~10). 유형 순서와는 다르다.
  -- 🔴 기본은 다섯이고, 화면의 「한 곳 더 적기」를 누른 사람만 여섯째부터
  --    늘어난다 (S15P21E201-754, migrations/0005_more_slots.sql).
  --    그래서 한 응답의 줄 수는 5~10 이고, 5 가 아니라고 이상한 것이 아니다.
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

  -- 🔴 상한 10 은 migrations/0005_more_slots.sql 과 **한 글자도 같아야 한다.**
  --    (그 파일은 살아 있는 DB 를, 이 줄은 새로 띄우는 DB 를 만든다. 둘이
  --     다르면 두 DB 가 서로 다른 표가 된다.)
  --    같은 값이 화면(index.html 의 MAX_SLOTS)과 서버(server.mjs 의 MAX_SLOTS)
  --    에도 있다. 셋이 어긋나면 사람은 화면에서 통과하고 여기서 거절당한다.
  CONSTRAINT recommendation_slot_ok CHECK (slot BETWEEN 1 AND 10),
  -- 🔴 여덟 값과 그 순서는 migrations/0006_add_sight_type.sql 과 **한 글자도
  --    같아야 한다.** (그 파일은 살아 있는 DB 를, 이 줄은 새로 띄우는 DB 를
  --    만든다. 둘이 다르면 두 DB 가 서로 다른 표가 된다.)
  --    같은 목록이 화면(index.html 의 TYPES)과 서버(server.mjs 의 PLACE_TYPES)
  --    에도 있다. 넷이 어긋나면 사람은 화면에서 통과하고 여기서 거절당한다.
  CONSTRAINT recommendation_place_type_ok CHECK (place_type IN (
    'SIGHT', 'FOOD', 'CAFE', 'NATURE', 'CULTURE', 'MARKET', 'ACTIVITY', 'BAR')),
  CONSTRAINT recommendation_when_good_ok CHECK (when_good IN ('DAY', 'NIGHT', 'ANY')),
  CONSTRAINT recommendation_place_name_ok CHECK (length(btrim(place_name)) BETWEEN 1 AND 60),
  CONSTRAINT recommendation_reason_ok     CHECK (length(btrim(reason))     BETWEEN 1 AND 500),
  -- 한 응답 안에서 칸 번호는 겹치지 않는다 (1~n 이 한 번씩)
  CONSTRAINT recommendation_one_per_slot UNIQUE (response_id, slot)

  -- 🔴 UNIQUE (response_id, place_type) 을 두지 않는다. 일부러 뺀 것이다.
  --    한 응답이 같은 유형을 다섯 번 적을 수 있어야 한다 — 맛집을 다섯 곳
  --    아는 사람의 답이 우리가 가장 원하는 답인데, 유형당 하나로 막으면
  --    그 답이 네 곳 잘려나간다.
  --    (2026-09-08 까지는 그 제약이 있었다. 걷어냈고, 되살리지 않는다.)
);

CREATE INDEX IF NOT EXISTS recommendation_response_idx ON recommendation (response_id);
CREATE INDEX IF NOT EXISTS recommendation_type_idx     ON recommendation (place_type);

-- ── 짝 비교: 문항 하나 = 줄 하나 ──────────────────────────────────
-- 🔴 아래는 migrations/0003_add_pairwise.sql 과 같은 것이다. 한 글자도
--    다르지 않게 맞춘다 (거기에 왜 이렇게 생겼는지가 자세히 적혀 있다).
--
-- 🔴 보여준 두 카드의 조건을 줄마다 그대로 적는다. design.mjs 를 열지 않아도
--    이 표만으로 조건부 로짓(여러 대안 중 하나를 고른 기록에서 각 조건의
--    무게를 역산하는 계산)을 돌릴 수 있어야 한다. 값을 채우는 것은
--    server.mjs 이고, 화면이 보낸 숫자가 아니라 자기가 가진 문항 정의에서
--    set_id 로 찾아 적는다.
CREATE TABLE IF NOT EXISTS pairwise_choice (
  id            BIGSERIAL PRIMARY KEY,
  response_id   BIGINT      NOT NULL REFERENCES response(id) ON DELETE CASCADE,

  design_id     TEXT        NOT NULL,
  set_id        TEXT        NOT NULL,

  -- 🔴 함정 문항인가. 성의를 재는 문항이지 취향을 재는 문항이 아니다 —
  --    계수 계산에 섞이면 아무거나 찍은 사람이 "아무거나 좋아하는 사람" 이 된다.
  is_trap       BOOLEAN     NOT NULL DEFAULT FALSE,
  trap_correct  SMALLINT,

  -- 화면 3장 중 몇 번째 장이었나 (한 장에 두 문항)
  page_no       SMALLINT    NOT NULL,

  -- 고른 쪽 (alt0 / alt1). 화면의 좌/우가 아니다
  chosen        SMALLINT    NOT NULL,
  -- 🔴 화면에서 **왼쪽**에 있던 쪽. 안 남기면 위치 편향을 나중에 못 뺀다.
  --    🔴 2026-09-12 (S15P21E201-875) top_was → left_was. 2차 화면부터 카드
  --       두 장이 위·아래가 아니라 좌·우로 놓인다. 값과 뜻은 그대로이고
  --       가리키는 방향만 바뀌었다 — 어느 쪽인지는 form_version 이 가른다
  --       (7 이하면 위, 8 이상이면 왼쪽). migrations/0008 참고.
  left_was      SMALLINT    NOT NULL,
  -- 🔴 걸린 시간은 초가 아니라 구간으로만 (0~7)
  ms_bucket     SMALLINT    NOT NULL,

  -- 보여준 카드 두 장의 조건
  -- 🔴 v2 의 칸이다. 속성이 다섯으로 고정이라 칸으로 박아 두었다.
  --    v3 부터는 아래 JSONB 가 대신 채워지고 여기는 NULL 이다.
  --    지우지 않는다 — 이미 들어온 v2 응답을 그대로 읽어야 하고, 칸을 지우는
  --    ALTER 는 되돌릴 수 없다. migrations/0007 이 NOT NULL 만 푼 것과 같다.
  alt0_price        INTEGER,
  alt0_walk_min     SMALLINT,
  alt0_queue_min    SMALLINT,
  alt0_same_street  SMALLINT,
  alt0_fame         TEXT,
  alt1_price        INTEGER,
  alt1_walk_min     SMALLINT,
  alt1_queue_min    SMALLINT,
  alt1_same_street  SMALLINT,
  alt1_fame         TEXT,

  -- ── 🔴 v3 (2026-09-11, S15P21E201-851) — migrations/0007 과 같은 것 ──
  -- v3 는 덩어리마다 속성이 다르다 — 「보는 곳」에는 가격이 없고 오르막·그늘이 있다.
  -- 'eat' · 'see' · 'play', 덩어리가 섞인 문항은 'mixed'. v2 행은 NULL 이다.
  block             TEXT,
  -- 섞인 문항일 때만. 두 카드의 덩어리가 다르다.
  alt0_block        TEXT,
  alt1_block        TEXT,
  -- 🔴 이 표만으로 조건부 로짓을 돌릴 수 있어야 한다 — design-v3.mjs 를 열지 않고도.
  --    값은 server.mjs 가 자기 문항 정의에서 set_id 로 찾아 적는다. 화면이 보낸
  --    숫자를 그대로 믿지 않는다.
  alt0              JSONB,
  alt1              JSONB,

  CONSTRAINT pairwise_choice_chosen_ok    CHECK (chosen    IN (0, 1)),
  CONSTRAINT pairwise_choice_left_was_ok  CHECK (left_was  IN (0, 1)),
  CONSTRAINT pairwise_choice_ms_bucket_ok CHECK (ms_bucket BETWEEN 0 AND 7),
  CONSTRAINT pairwise_choice_page_no_ok   CHECK (page_no   BETWEEN 1 AND 99),
  CONSTRAINT pairwise_choice_fame_ok      CHECK (
    (alt0_fame IS NULL OR alt0_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS'))
    AND (alt1_fame IS NULL OR alt1_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS'))),
  -- 🔴 두 카드가 달라야 한다. 판에 따라 채워지는 쪽이 달라서 채워진 쪽을 본다.
  CONSTRAINT pairwise_choice_differ_ok    CHECK (
    CASE
      WHEN alt0 IS NOT NULL AND alt1 IS NOT NULL
        THEN alt0 IS DISTINCT FROM alt1 OR alt0_block IS DISTINCT FROM alt1_block
      ELSE (alt0_price, alt0_walk_min, alt0_queue_min, alt0_same_street, alt0_fame) IS DISTINCT FROM
           (alt1_price, alt1_walk_min, alt1_queue_min, alt1_same_street, alt1_fame)
    END),
  -- 🔴 어느 모양이든 한쪽은 반드시 채워져야 한다. 둘 다 비면 그 줄은
  --    「무엇을 보여줬는지 모르는 선택」이 되어 추정에 못 쓴다.
  CONSTRAINT ck_pairwise_choice_shape     CHECK (
    (alt0 IS NOT NULL AND alt1 IS NOT NULL)
    OR (alt0_price IS NOT NULL AND alt1_price IS NOT NULL)),
  -- 🔴 목록이 design-v3.mjs 의 BLOCKS 와 두 벌이다. 덩어리를 늘리면 여기도 고친다.
  CONSTRAINT ck_pairwise_choice_block     CHECK (
    (block      IS NULL OR block      IN ('eat', 'see', 'play', 'mixed'))
    AND (alt0_block IS NULL OR alt0_block IN ('eat', 'see', 'play'))
    AND (alt1_block IS NULL OR alt1_block IN ('eat', 'see', 'play'))),
  CONSTRAINT pairwise_choice_trap_ok      CHECK (
    -- 🔴 IS NOT NULL 을 반드시 앞에 둔다. `NULL IN (0,1)` 은 FALSE 가
    --    아니라 NULL 이고, CHECK 는 NULL 을 통과시킨다 — 그래서 예전 판은
    --    "함정인데 정답 칸이 빈" 줄을 안 막았다. 실측으로 잡았다.
    CASE WHEN is_trap THEN trap_correct IS NOT NULL AND trap_correct IN (0, 1)
         ELSE trap_correct IS NULL
    END),
  CONSTRAINT pairwise_choice_one_per_set  UNIQUE (response_id, set_id)
);

CREATE INDEX IF NOT EXISTS pairwise_choice_response_idx ON pairwise_choice (response_id);
CREATE INDEX IF NOT EXISTS pairwise_choice_set_idx      ON pairwise_choice (set_id);
CREATE INDEX IF NOT EXISTS pairwise_choice_block_idx    ON pairwise_choice (block);

-- 🔴 추정에는 이 뷰를 쓴다. 함정이 빠져 있다.
CREATE OR REPLACE VIEW pairwise_choice_real AS
  SELECT * FROM pairwise_choice WHERE is_trap IS FALSE;

COMMIT;
