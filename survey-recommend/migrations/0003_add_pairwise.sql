-- S15P21E201-754 — 짝 비교 (두 곳 중 하나 고르기) 여섯 문항, 화면 3장
--
-- 🔴 이 파일은 자동으로 안 돈다. schema.sql 은 postgres 볼륨이 "처음
--    비어 있을 때"만 돈다(docker-entrypoint-initdb.d). 이미 떠 있는 설문
--    서버는 볼륨이 안 비어 있으니 schema.sql 을 다시 고쳐도 반영이 안 된다 —
--    그래서 이 ALTER 문을 손으로(또는 배포 스크립트로) 한 번 돌려야 한다.
--
-- 실행 (0002 를 먼저 돌린 뒤):
--   docker exec -i survey-postgres psql -U survey -d survey \
--     < survey-recommend/migrations/0003_add_pairwise.sql
--
-- ── 왜 이 문항이 필요한가 ────────────────────────────────────────
-- 개인화 세 문항(0002)이 내는 것은 **방향(부호)** 뿐이다. "이 사람은 값을 더
-- 본다" 까지는 알려 주지만 **얼마나** 더 보는지(배수)는 안 알려 준다.
-- 배수가 1.0 이면 추천 순위는 한 칸도 안 바뀐다. 그 배수는 "조건이 다른 두 곳
-- 중 하나 고르기" 를 여러 번 시켜야만 나온다.
--
-- 🔴 같은 사람에게서 둘 다 받아야 이을 수 있다. 그래서 같은 설문 안에서
--    이어 묻고, 두 답이 response 의 **같은 줄**에 들어간다. 이을 새 식별자를
--    만들지 않는다 — 그 줄의 nonce 가 이미 응답 하나를 가리킨다.
--
-- 재는 속성은 다섯이다: 가격 · 걷는 거리 · 줄서기 · 골목의 비슷한 가게 ·
-- 알려진 정도. (bigData/process/choice-design.mjs 의 속성 — 걷는 시간 · 경사 ·
-- 계단 · 환승 — 과 다르다. 그쪽은 **경로 비용**이고 여기는 **장소 추천 순위**다.)
--
-- 🔴 response 에 더하는 칸은 전부 NULL 허용이다. 이미 들어와 있는 응답은
--    NULL 로 남고, 그게 "짝 비교를 아예 안 물어봤다" 는 뜻이다.

BEGIN;

-- ── 응답 한 줄에 붙는 요약 셋 ─────────────────────────────────────
-- NULL = 안 물어봤음, 'ANSWERED' = 여섯 문항을 다 답했음.
-- 🔴 'SKIPPED' 가 없는 것은 빠뜨린 게 아니다 — 짝 비교 화면에는 건너뛰기가
--    없다. 두 카드 중 하나를 눌러야 다음으로 간다 (강제 선택이 짝 비교의
--    본체다. 건너뛸 수 있으면 배수를 아무것도 못 잰다). 나중에 건너뛰기를
--    만들면 그때 값을 더한다.
ALTER TABLE response ADD COLUMN IF NOT EXISTS pairwise_status      TEXT;
-- 어느 문항 세트에 답했나 ('recommend-v1-seed-20260907'). 문항이 또 바뀔 수
-- 있으므로, 어느 판의 문항이었는지가 행에 남아야 나중에 섞이지 않는다.
ALTER TABLE response ADD COLUMN IF NOT EXISTS pairwise_design_id   TEXT;
-- 함정 문항을 통과했나. 아래 pairwise_choice 의 함정 줄에서 나오는 값이지만
-- 여기에도 둔다 — "이 사람 응답을 통째로 뺄까" 는 응답 단위로 거르는 일이라,
-- 매번 짝 비교 표를 뒤지게 하면 아무도 안 거른다.
-- 🔴 두 곳이 어긋날 수 없게, 둘 다 server.mjs 가 **같은 트랜잭션**에서
--    같은 문항 정의를 보고 적는다.
ALTER TABLE response ADD COLUMN IF NOT EXISTS pairwise_trap_passed BOOLEAN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'response_pairwise_status_ok') THEN
    ALTER TABLE response ADD CONSTRAINT response_pairwise_status_ok
      CHECK (pairwise_status IS NULL OR pairwise_status = 'ANSWERED');
  END IF;

  -- 답했으면 어느 문항 세트였는지와 함정 결과가 반드시 있고,
  -- 안 물어봤으면 둘 다 실릴 수 없다.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_pairwise_value_matches_status') THEN
    ALTER TABLE response ADD CONSTRAINT ck_pairwise_value_matches_status
      -- 🔴 CASE 로 쓴다. OR 로 늘어놓으면 상태가 NULL 일 때 각 가지가
      --    TRUE/FALSE 가 아니라 **NULL** 이 되고, CHECK 는 NULL 을 통과시킨다
      --    (SQL 의 3값 논리 — 거짓이 아니라 "모른다" 라서 안 막는다).
      CHECK (
        CASE WHEN pairwise_status = 'ANSWERED'
             THEN pairwise_design_id IS NOT NULL
                  AND length(btrim(pairwise_design_id)) BETWEEN 1 AND 60
                  AND pairwise_trap_passed IS NOT NULL
             ELSE pairwise_design_id IS NULL AND pairwise_trap_passed IS NULL
        END
      );
  END IF;
END $$;

-- ── 문항 하나 = 줄 하나 ────────────────────────────────────────────
-- 🔴 짝 비교 답을 JSONB 한 칸에 밀어 넣지 않는다. 한 칸에 넣으면 세는 것부터
--    다시 풀어야 하고, 추정에 넣으려면 또 한 번 풀어야 한다.
--
-- 🔴 **보여준 두 카드의 조건을 이 줄에 그대로 적는다.** design.mjs 를 열지
--    않아도 이 표만으로 조건부 로짓(conditional logit — "여러 대안 중 하나를
--    고른 기록에서 각 조건의 무게를 역산하는 계산")을 돌릴 수 있어야 한다.
--    set_id 만 적어 두면 씨앗이나 design.mjs 가 바뀌는 순간 옛 응답이
--    **무엇에 대한 답이었는지 영영 못 읽는다.**
--
--    값을 채우는 것은 server.mjs 다. 화면이 보낸 숫자를 그대로 믿지 않고,
--    화면 안의 문항 정의에서 set_id 로 찾아 서버가 적는다.
--
-- 새로 만드는 표라 여기서는 NOT NULL 을 걸어도 안전하다 — 채울 옛 행이 없다.
CREATE TABLE IF NOT EXISTS pairwise_choice (
  id            BIGSERIAL PRIMARY KEY,
  response_id   BIGINT      NOT NULL REFERENCES response(id) ON DELETE CASCADE,

  design_id     TEXT        NOT NULL,
  set_id        TEXT        NOT NULL,

  -- 🔴 함정 문항인가. 성의를 재는 문항이지 취향을 재는 문항이 아니다.
  --    계수 계산에 한 줄이라도 섞이면, 아무거나 찍은 사람이 "아무거나
  --    좋아하는 사람" 으로 학습된다. 추정에는 아래 pairwise_choice_real 뷰를 쓴다.
  is_trap       BOOLEAN     NOT NULL DEFAULT FALSE,
  -- 함정에서 "안 찍은 사람이라면 골랐어야 하는 쪽". 함정이 아니면 NULL.
  trap_correct  SMALLINT,

  -- 화면 3장 중 몇 번째 장이었나 (한 장에 두 문항). 뒤로 갈수록 대충 찍는지 본다.
  page_no       SMALLINT    NOT NULL,

  -- 고른 쪽. 아래 alt0_* / alt1_* 중 어느 쪽인지다. 화면의 위/아래가 아니다.
  chosen        SMALLINT    NOT NULL,

  -- 🔴 화면에서 **위에 있던 쪽**. 두 카드는 세로로 쌓여 있고, 화면이 문항마다
  --    위아래를 무작위로 뒤집는다. 이걸 안 남기면 위치 편향(사람이 위쪽을 더
  --    고르는 성향)을 나중에 못 뺀다 — 추정할 때 설명변수로 하나 넣는다.
  top_was       SMALLINT    NOT NULL,

  -- 🔴 몇 초 걸렸는지를 초가 아니라 **구간**으로만 남긴다 (0~7).
  --    안 읽고 찍은 응답을 거르는 데는 구간이면 충분하고,
  --    초 단위는 그 이상(재식별)에 쓸모가 있다.
  ms_bucket     SMALLINT    NOT NULL,

  -- ── 보여준 카드 두 장의 조건 ──────────────────────────────────
  -- 가격(원) · 역에서 걸어서(분) · 줄서기(분) · 같은 골목의 비슷한 가게(곳) ·
  -- 알려진 정도(LOCAL_ONLY | SNS_FAMOUS).
  alt0_price        INTEGER  NOT NULL,
  alt0_walk_min     SMALLINT NOT NULL,
  alt0_queue_min    SMALLINT NOT NULL,
  alt0_same_street  SMALLINT NOT NULL,
  alt0_fame         TEXT     NOT NULL,
  alt1_price        INTEGER  NOT NULL,
  alt1_walk_min     SMALLINT NOT NULL,
  alt1_queue_min    SMALLINT NOT NULL,
  alt1_same_street  SMALLINT NOT NULL,
  alt1_fame         TEXT     NOT NULL,

  CONSTRAINT pairwise_choice_chosen_ok    CHECK (chosen    IN (0, 1)),
  CONSTRAINT pairwise_choice_top_was_ok   CHECK (top_was   IN (0, 1)),
  CONSTRAINT pairwise_choice_ms_bucket_ok CHECK (ms_bucket BETWEEN 0 AND 7),
  CONSTRAINT pairwise_choice_page_no_ok   CHECK (page_no   BETWEEN 1 AND 99),
  CONSTRAINT pairwise_choice_fame_ok      CHECK (alt0_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS')
                                            AND alt1_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS')),
  -- 두 카드가 똑같으면 물어볼 것이 없다 — 그런 줄은 들어오면 안 된다
  CONSTRAINT pairwise_choice_differ_ok    CHECK (
    (alt0_price, alt0_walk_min, alt0_queue_min, alt0_same_street, alt0_fame) IS DISTINCT FROM
    (alt1_price, alt1_walk_min, alt1_queue_min, alt1_same_street, alt1_fame)),
  -- 함정이면 정답이 있고, 함정이 아니면 정답 칸이 비어 있어야 한다
  CONSTRAINT pairwise_choice_trap_ok      CHECK (
    -- 🔴 IS NOT NULL 을 반드시 앞에 둔다. `NULL IN (0,1)` 은 FALSE 가
    --    아니라 NULL 이고, CHECK 는 NULL 을 통과시킨다 — 그래서 예전 판은
    --    "함정인데 정답 칸이 빈" 줄을 안 막았다. 실측으로 잡았다.
    CASE WHEN is_trap THEN trap_correct IS NOT NULL AND trap_correct IN (0, 1)
         ELSE trap_correct IS NULL
    END),
  -- 한 응답 안에서 같은 문항이 두 번 오지 않는다
  CONSTRAINT pairwise_choice_one_per_set  UNIQUE (response_id, set_id)
);

CREATE INDEX IF NOT EXISTS pairwise_choice_response_idx ON pairwise_choice (response_id);
CREATE INDEX IF NOT EXISTS pairwise_choice_set_idx      ON pairwise_choice (set_id);

-- 🔴 추정에는 이 뷰를 쓴다. 함정이 빠져 있다.
--    표를 직접 읽으면 언젠가 누군가 WHERE is_trap = FALSE 를 빠뜨리고,
--    그날 나온 계수는 틀렸는데 틀린 티가 안 난다.
CREATE OR REPLACE VIEW pairwise_choice_real AS
  SELECT * FROM pairwise_choice WHERE is_trap IS FALSE;

COMMIT;
