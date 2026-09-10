-- S15P21E201-754 — 설문 맨 앞의 개인화 세 문항 (오는 교통 · 숙소 · 식사)
--
-- 🔴 이 파일은 자동으로 안 돈다. schema.sql 은 postgres 볼륨이 "처음
--    비어 있을 때"만 돈다(docker-entrypoint-initdb.d). 이미 떠 있는 설문
--    서버는 볼륨이 안 비어 있으니 schema.sql 을 다시 고쳐도 반영이 안 된다 —
--    그래서 이 ALTER 문을 손으로(또는 배포 스크립트로) 한 번 돌려야 한다.
--
-- 실행:
--   docker exec -i survey-postgres psql -U survey -d survey \
--     < survey-recommend/migrations/0002_add_spend_profile.sql
--
-- 무엇을 하는가 — schema.sql 의 response 표에 이미 반영해 둔 것과 같은 칸을
-- 기존 표에 더한다. 🔴 전부 NULL 허용이라 이미 들어와 있는 응답은 안 건드린다.
--    (NOT NULL 을 걸면 값이 없는 옛 행 때문에 ALTER 자체가 거부되고,
--     그 순간 살아 있는 설문이 멈춘다.)
--
-- 🔴 NULL 이 곧 "안 물어봤음" 이다. 'UNKNOWN' 같은 글자를 따로 두지 않는다 —
--    두 벌이 되면 한쪽만 고쳐지고, 그때부터 둘이 어긋난 채로 굴러간다.
--    골랐다(SELECTED) / 봤는데 건너뛰었다(SKIPPED) / 안 물어봤다(NULL) 셋은
--    서로 다른 것이고, 뭉개면 "이 문항이 이탈을 만드나" 를 영영 못 본다.

BEGIN;

ALTER TABLE response ADD COLUMN IF NOT EXISTS spend_profile        JSONB;
ALTER TABLE response ADD COLUMN IF NOT EXISTS spend_profile_status TEXT;

-- 🔴 이 응답이 **어느 판의 설문**에 답했나.
--    설문이 살아 있는 동안 문항이 늘어나므로, 이 표시가 없으면 나중에
--    반드시 이렇게 잘못 읽는다: "응답 100건 중 세 문항 답이 60건뿐이네
--    → 응답률 60%". 사실은 40건이 그 문항이 생기기 **전에** 들어온 것이라
--    응답률은 100% 다.
--
--      NULL 또는 1 = 이 칸이 생기기 전 판 (추천 다섯 곳 · 전화번호 · 가게 자동완성)
--      2           = 개인화 세 문항이 붙은 판
--      3           = 짝 비교까지 붙은 판  (migrations/0003)
--
--    DEFAULT 1 이라 **이미 들어와 있는 행은 전부 1** 이 된다. 그게 사실이다.
--    새 행은 server.mjs 가 자기 판 번호를 실어 보낸다.
--    🔴 DEFAULT 는 두되 NOT NULL 은 걸지 않는다 — 위와 같은 이유다.
ALTER TABLE response ADD COLUMN IF NOT EXISTS form_version SMALLINT DEFAULT 1;

-- 제약에는 IF NOT EXISTS 가 없다. 두 번 돌려도 안 깨지게 pg_constraint 를 본다.
-- 🔴 이름과 내용을 schema.sql 과 한 글자도 다르지 않게 맞춘다. 둘이 다르면
--    새로 띄운 DB 와 살아 있는 DB 가 서로 다른 표가 된다.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'response_spend_profile_status_ok') THEN
    ALTER TABLE response ADD CONSTRAINT response_spend_profile_status_ok
      CHECK (spend_profile_status IS NULL OR spend_profile_status IN ('SELECTED', 'SKIPPED'));
  END IF;

  -- 고른 답에는 값이 반드시 있고, 건너뜀·안 물어봄에는 값을 실을 수 없다.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_spend_profile_value_matches_status') THEN
    ALTER TABLE response ADD CONSTRAINT ck_spend_profile_value_matches_status
      -- 🔴 CASE 로 쓴다. OR 로 늘어놓으면 상태가 NULL 일 때 각 가지가
      --    TRUE/FALSE 가 아니라 **NULL** 이 되고, CHECK 는 NULL 을 통과시킨다
      --    (SQL 의 3값 논리 — 거짓이 아니라 "모른다" 라서 안 막는다).
      --    실제로 안 막히는 것을 확인하고 고쳤다.
      CHECK (
        CASE WHEN spend_profile_status = 'SELECTED'
             THEN spend_profile IS NOT NULL
                  AND jsonb_typeof(spend_profile) = 'object'
                  AND spend_profile <> '{}'::jsonb
             ELSE spend_profile IS NULL
        END
      );
  END IF;

  -- 🔴 답한 키만 들어간다. 건너뛴 키는 null 이 아니라 아예 없다.
  --    "stay": null 과 stay 키가 없는 것은 나중에 반드시 누군가 다르게 읽는다.
  --    바깥 껍데기(키 이름)는 여기서 막고, 안쪽 코드값은 server.mjs 의
  --    SPEND_CHOICES 가 막는다.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_spend_profile_keys_known') THEN
    ALTER TABLE response ADD CONSTRAINT ck_spend_profile_keys_known
      CHECK (
        spend_profile IS NULL
        OR spend_profile - ARRAY['transport', 'stay', 'meal'] = '{}'::jsonb
      );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'response_form_version_ok') THEN
    ALTER TABLE response ADD CONSTRAINT response_form_version_ok
      CHECK (form_version IS NULL OR form_version >= 1);
  END IF;
END $$;

COMMIT;
