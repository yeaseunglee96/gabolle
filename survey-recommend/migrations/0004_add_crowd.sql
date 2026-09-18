-- S15P21E201-754 — 네 번째 개인화 문항: 저녁 먹을 곳의 **북적임**
--
-- 🔴 이 파일은 자동으로 안 돈다. schema.sql 은 postgres 볼륨이 "처음
--    비어 있을 때"만 돈다(docker-entrypoint-initdb.d). 이미 떠 있는 설문
--    서버는 볼륨이 안 비어 있으니 schema.sql 을 다시 고쳐도 반영이 안 된다 —
--    그래서 이 ALTER 문을 손으로(또는 배포 스크립트로) 한 번 돌려야 한다.
--
-- 실행 (0002 · 0003 을 먼저 돌린 뒤):
--   docker exec -i survey-postgres psql -U survey -d survey \
--     < survey-recommend/migrations/0004_add_crowd.sql
--
-- ── 왜 이 문항이 필요한가 ────────────────────────────────────────
-- 장소 쪽 피처는 이미 있다. 선정 모델이 "100m 안 음식점 수 · 300m 안 음식점
-- 수 · 같은 업종 밀도" 를 이미 잰다 — **북적임이 곧 그 밀도다.** 없는 것은
-- 그 밀도를 어느 쪽으로 밀어야 하는지를 정하는 **사람 쪽 값**이다. 그게 없으면
-- 밀도 피처는 계산은 되는데 어느 방향으로 쓸지를 아무도 모른다.
--
-- 🔴 선호를 직접 묻지 않는다. "북적이는 곳 좋아하세요?" 는 사람이 자기 답을
--    모른다 — 그때그때 다르다고 여기면서도 아무거나 고른다. 그래서 상황과
--    행동을 묻는다: "저녁 먹을 곳을 고를 때 보통 어느 쪽으로 가세요".
--    소득을 안 묻고 "숙소에 1박 얼마까지" 를 묻는 것과 같은 간접 대리 방식이다
--    (docs/COLDSTART-THREE-QUESTIONS.md 1절).
--
-- ── 🔴 왜 spend_profile JSONB 에 키를 하나 더 넣지 않았나 ─────────
-- spend_profile 은 **지불 의사** 한 차원이다(SPEND_PROFILE). 북적임은 돈이
-- 아니라 밀도라서 다른 차원이고, 같은 JSONB 에 넣으면 상태 칸 하나
-- (spend_profile_status)를 두 차원이 나눠 쓰게 된다 — "북적임만 답하고 돈
-- 문항 셋은 다 건너뛴 사람" 이 spend_profile_status='SELECTED' 로 남고,
-- 그 줄로 지불 의사 등급을 계산하면 조용히 틀린다. 그래서 칸을 따로 둔다.
--
-- ── 🔴 새 칸은 전부 NULL 허용이다 ────────────────────────────────
-- NULL 이 곧 "그 판에서는 안 물어봤음" 이다. 이미 응답이 들어 있는 표에
-- NOT NULL 을 걸면 값이 없는 옛 행 때문에 ALTER 자체가 거부되고, 그 순간
-- 살아 있는 설문이 멈춘다.

BEGIN;

-- 고른 코드값 하나. NULL = 안 물어봤음 또는 건너뜀 (어느 쪽인지는 아래 상태 칸이 가른다).
ALTER TABLE response ADD COLUMN IF NOT EXISTS crowd_pref        TEXT;
-- NULL = 안 물어봤음 · 'SELECTED' = 골랐음 · 'SKIPPED' = 봤는데 안 골랐음.
-- 🔴 셋을 뭉개지 않는다. 값이 비어 있는 이유가 셋이라 값만으로는 못 가린다 —
--    뭉개면 "이 문항이 이탈을 만드나" 를 영영 못 본다.
ALTER TABLE response ADD COLUMN IF NOT EXISTS crowd_pref_status TEXT;

-- 🔴 form_version 은 0002 가 이미 만들었다. 여기서는 칸을 더하지 않고
--    **뜻만 하나 늘어난다**:
--      NULL 또는 1 = 이 칸이 생기기 전 판 (추천 다섯 곳 · 전화번호 · 가게 자동완성)
--      2           = 개인화 세 문항이 붙은 판
--      3           = 짝 비교까지 붙은 판
--      4           = **북적임 문항까지 붙은 판** (이 파일)
--    안 올리면 나중에 반드시 이렇게 잘못 읽는다: "응답 100건 중 북적임 답이
--    60건뿐이네 → 응답률 60%". 사실은 40건이 그 문항이 생기기 **전에** 들어온
--    것이라 응답률은 100% 다. 숫자를 올리는 것은 server.mjs 의 FORM_VERSION 이다.

-- 제약에는 IF NOT EXISTS 가 없다. 두 번 돌려도 안 깨지게 pg_constraint 를 본다.
-- 🔴 이름과 내용을 schema.sql 과 한 글자도 다르지 않게 맞춘다. 둘이 다르면
--    새로 띄운 DB 와 살아 있는 DB 가 서로 다른 표가 된다.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'response_crowd_pref_status_ok') THEN
    ALTER TABLE response ADD CONSTRAINT response_crowd_pref_status_ok
      CHECK (crowd_pref_status IS NULL OR crowd_pref_status IN ('SELECTED', 'SKIPPED'));
  END IF;

  -- 🔴 이 칸은 JSONB 가 아니라 글자 한 칸이라, 코드값 목록을 DB 가 직접 막는다
  --    (age_band · busan_years · place_type 과 같은 방식이다. spend_profile 은
  --     JSONB 안쪽이라 못 막고 server.mjs 가 막는다 — 여기는 막을 수 있으니 막는다).
  --    🔴 목록이 server.mjs 의 CROWD_CHOICES 와 두 벌이다. 고칠 때 반드시 같이 고친다.
  --
  --    눈금은 "저녁 먹을 곳을 고를 때 어느 쪽으로 가나" 한 축이다:
  --      CROWD_BUSY   사람이 몰리는 먹자골목 한가운데   (밀도 높은 쪽)
  --      CROWD_EDGE   그 골목 가장자리 · 한 블록 안쪽   (가운데)
  --      CROWD_QUIET  가게가 드문 조용한 동네 골목      (밀도 낮은 쪽)
  --      CROWD_VARIES 그날그날 달라요
  --    🔴 CROWD_VARIES 는 눈금 위의 한 점이 **아니다.** 건너뛴 것도 아니다 —
  --       "밀도를 고정하지 않는 사람" 이라는 답이다. 가운데(CROWD_EDGE)로
  --       뭉개면 "가장자리를 고른 사람" 과 구별이 영영 안 된다. 저장은 하고
  --       계산(밀도 방향)에서만 뺀다. 식사 문항의 'VARIES' 와 같은 취급이다.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'response_crowd_pref_ok') THEN
    ALTER TABLE response ADD CONSTRAINT response_crowd_pref_ok
      CHECK (crowd_pref IS NULL OR crowd_pref IN (
        'CROWD_BUSY', 'CROWD_EDGE', 'CROWD_QUIET', 'CROWD_VARIES'));
  END IF;

  -- 고른 답에는 값이 반드시 있고, 건너뜀 · 안 물어봄에는 값을 실을 수 없다.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_crowd_pref_value_matches_status') THEN
    ALTER TABLE response ADD CONSTRAINT ck_crowd_pref_value_matches_status
      -- 🔴 CASE 로 쓴다. OR 로 늘어놓으면 상태가 NULL 일 때 각 가지가
      --    TRUE/FALSE 가 아니라 **NULL** 이 되고, CHECK 는 NULL 을 통과시킨다
      --    (SQL 의 3값 논리 — 거짓이 아니라 "모른다" 라서 안 막는다).
      --    0002 · 0003 에서 실제로 안 막히는 것을 확인하고 고친 자리다.
      CHECK (
        CASE WHEN crowd_pref_status = 'SELECTED'
             THEN crowd_pref IS NOT NULL
             ELSE crowd_pref IS NULL
        END
      );
  END IF;
END $$;

COMMIT;
