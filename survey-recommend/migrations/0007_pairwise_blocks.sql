-- 0007 — 짝 비교를 덩어리 셋으로 (S15P21E201-851)
--
-- 🔴 살아 있는 DB 에 돌리는 파일이다. schema.sql 과 **같은 결과**를 내야 한다 —
--    칸 이름 · 제약 이름 · 제약 내용을 한 글자도 다르지 않게 맞춘다.
--    한쪽만 고치면 새로 띄운 DB 와 살아 있는 DB 가 서로 다른 표가 되고,
--    그건 두 DB 의 응답을 합치려는 날에야 드러난다.
--
-- ── 무엇이 바뀌나 ────────────────────────────────────────────────────
-- v2 는 카드의 속성이 **다섯으로 고정**이라 칸을 박아 두었다
-- (alt0_price · alt0_walk_min · alt0_queue_min · alt0_same_street · alt0_fame).
-- v3 는 **덩어리마다 속성이 다르다** — 「보는 곳」에는 가격이 없고 오르막·그늘이 있다.
-- 박힌 칸으로는 담을 수 없다.
--
-- 🔴 그래서 칸을 **더한다. 지우지 않는다.**
--    ① 이미 들어온 v2 응답을 그대로 읽을 수 있어야 한다
--    ② 칸을 지우는 ALTER 는 되돌릴 수 없다 — 값까지 같이 사라진다
--    옛 칸은 NOT NULL 만 푼다. v3 행에서는 NULL 로 남는다.
--
-- 🔴 새로 더하는 칸은 전부 NULL 허용이다. 이미 응답이 들어 있는 표에 NOT NULL 을
--    걸면 ALTER 가 그 자리에서 거부되고 살아 있는 설문이 멈춘다.
--
-- 🔴 두 번 돌려도 안 깨져야 한다. 배포가 두 번 돌 수 있다.
--
-- 돌리는 법:
--   docker exec -i survey-postgres psql -U survey -d survey -v ON_ERROR_STOP=1 \
--     < survey-recommend/migrations/0007_pairwise_blocks.sql

BEGIN;

-- ── ① 어느 덩어리의 문항이었나 ───────────────────────────────────────
-- 'eat' · 'see' · 'play', 그리고 덩어리가 섞인 문항은 'mixed'.
-- 🔴 v2 행은 NULL 이다. NULL 이 곧 "덩어리가 없던 판" 이고, form_version 이
--    그것을 가른다 — 7 이상이면 덩어리가 있던 판이다.
ALTER TABLE pairwise_choice ADD COLUMN IF NOT EXISTS block TEXT;

-- 섞인 문항은 두 카드의 덩어리가 다르다. 그때만 채운다.
ALTER TABLE pairwise_choice ADD COLUMN IF NOT EXISTS alt0_block TEXT;
ALTER TABLE pairwise_choice ADD COLUMN IF NOT EXISTS alt1_block TEXT;

-- ── ② 보여준 카드 두 장의 조건 ───────────────────────────────────────
-- 🔴 JSONB 다. 덩어리마다 키가 다르므로 칸으로 못 박는다.
--    값을 채우는 것은 server.mjs 이고, 화면이 보낸 숫자가 아니라 **자기가 가진
--    문항 정의에서 set_id 로 찾아 적는다.** (v2 와 같은 원칙이다)
--    이 표만으로 조건부 로짓을 돌릴 수 있어야 한다 — design-v3.mjs 를 열지 않고도.
ALTER TABLE pairwise_choice ADD COLUMN IF NOT EXISTS alt0 JSONB;
ALTER TABLE pairwise_choice ADD COLUMN IF NOT EXISTS alt1 JSONB;

-- ── ③ 옛 칸의 NOT NULL 을 푼다 ──────────────────────────────────────
-- 🔴 이게 없으면 v3 행이 INSERT 되는 순간 거부당한다. 칸 자체는 남긴다.
ALTER TABLE pairwise_choice ALTER COLUMN alt0_price       DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt0_walk_min    DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt0_queue_min   DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt0_same_street DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt0_fame        DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt1_price       DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt1_walk_min    DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt1_queue_min   DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt1_same_street DROP NOT NULL;
ALTER TABLE pairwise_choice ALTER COLUMN alt1_fame        DROP NOT NULL;

-- ── ④ 옛 제약 둘을 v3 행이 통과할 수 있게 고친다 ────────────────────
-- 🔴 pairwise_choice_fame_ok 는 alt0_fame 이 두 값 중 하나여야 한다고 못 박는다.
--    v3 행은 그 칸이 NULL 이라 `NULL IN (...)` 가 NULL 이 되어 통과하지만,
--    **통과하는 이유가 「모른다」라서**다. 의도를 분명히 적어 다시 만든다.
ALTER TABLE pairwise_choice DROP CONSTRAINT IF EXISTS pairwise_choice_fame_ok;
ALTER TABLE pairwise_choice ADD CONSTRAINT pairwise_choice_fame_ok CHECK (
  (alt0_fame IS NULL OR alt0_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS'))
  AND (alt1_fame IS NULL OR alt1_fame IN ('LOCAL_ONLY', 'SNS_FAMOUS')));

-- 🔴 두 카드가 달라야 한다는 제약. v2 는 박힌 칸 다섯을 비교했다.
--    v3 는 JSONB 를 비교한다. 판에 따라 어느 쪽이 채워지는지 다르므로
--    **채워진 쪽을 본다.**
ALTER TABLE pairwise_choice DROP CONSTRAINT IF EXISTS pairwise_choice_differ_ok;
ALTER TABLE pairwise_choice ADD CONSTRAINT pairwise_choice_differ_ok CHECK (
  CASE
    WHEN alt0 IS NOT NULL AND alt1 IS NOT NULL
      THEN alt0 IS DISTINCT FROM alt1 OR alt0_block IS DISTINCT FROM alt1_block
    ELSE (alt0_price, alt0_walk_min, alt0_queue_min, alt0_same_street, alt0_fame) IS DISTINCT FROM
         (alt1_price, alt1_walk_min, alt1_queue_min, alt1_same_street, alt1_fame)
  END);

-- ── ⑤ 어느 모양이든 한쪽은 반드시 채워져 있어야 한다 ─────────────────
-- 🔴 둘 다 비면 그 줄은 "무엇을 보여줬는지 모르는 선택" 이 되고, 추정에 못 쓴다.
--    그런 줄은 있으나 마나가 아니라 **나중에 표를 못 믿게 만든다.**
ALTER TABLE pairwise_choice DROP CONSTRAINT IF EXISTS ck_pairwise_choice_shape;
ALTER TABLE pairwise_choice ADD CONSTRAINT ck_pairwise_choice_shape CHECK (
  (alt0 IS NOT NULL AND alt1 IS NOT NULL)
  OR (alt0_price IS NOT NULL AND alt1_price IS NOT NULL));

-- ── ⑥ 덩어리 이름은 아는 것만 ───────────────────────────────────────
-- 🔴 목록이 design-v3.mjs 의 BLOCKS 와 두 벌이다. 덩어리를 늘리면 여기도 고친다.
ALTER TABLE pairwise_choice DROP CONSTRAINT IF EXISTS ck_pairwise_choice_block;
ALTER TABLE pairwise_choice ADD CONSTRAINT ck_pairwise_choice_block CHECK (
  (block      IS NULL OR block      IN ('eat', 'see', 'play', 'mixed'))
  AND (alt0_block IS NULL OR alt0_block IN ('eat', 'see', 'play'))
  AND (alt1_block IS NULL OR alt1_block IN ('eat', 'see', 'play')));

-- ── ⑦ 추정용 뷰는 그대로 — 함정만 뺀다 ─────────────────────────────
CREATE OR REPLACE VIEW pairwise_choice_real AS
  SELECT * FROM pairwise_choice WHERE is_trap IS FALSE;

-- 덩어리별로 뽑는 일이 잦다
CREATE INDEX IF NOT EXISTS pairwise_choice_block_idx ON pairwise_choice (block);

COMMIT;
