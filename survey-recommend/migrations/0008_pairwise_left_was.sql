-- 0008 — 카드가 위·아래에서 좌·우로 간다. top_was 를 left_was 로 (S15P21E201-875)
--
-- 🔴 살아 있는 DB 에 돌리는 파일이다. schema.sql 과 **같은 결과**를 내야 한다 —
--    칸 이름 · 제약 이름 · 제약 내용을 한 글자도 다르지 않게 맞춘다.
--    한쪽만 고치면 새로 띄운 DB 와 살아 있는 DB 가 서로 다른 표가 되고,
--    그건 두 DB 의 응답을 합치려는 날에야 드러난다.
--
-- ── 왜 이름을 바꾸나 ─────────────────────────────────────────────────
-- 2차 화면 설계부터 짝 비교 카드 두 장이 **위·아래가 아니라 좌·우**로 놓인다.
-- 속성 이름과 아이콘을 가운데 열에 한 번만 그려 좌우 대칭이 저절로 보장되게
-- 하려는 것이다.
--
-- 그러면 `top_was`("화면에서 위에 있던 쪽")가 실제로는 **왼쪽에 있던 쪽**을
-- 가리키게 된다. 값은 여전히 0/1 이고 뜻도 여전히 "위치 편향을 나중에 빼기
-- 위한 표시" 지만, **이름이 거짓말을 한다.**
--
-- 🔴 이름을 안 고치고 주석으로만 적어 두는 길도 있었다. 사람이 골랐다 —
--    칸 이름은 분석 쿼리를 짜는 사람이 제일 먼저 보는 것이고, 그 사람은
--    이 주석을 안 읽는다.
--
-- ── 옛 응답은 어떻게 되나 ────────────────────────────────────────────
-- 🔴 값을 손대지 않는다. RENAME 은 이름만 바꾼다.
--    · form_version <= 7 인 행 → 그 0/1 은 "위에 있던 쪽" 이다
--    · form_version >= 8 인 행 → 그 0/1 은 "왼쪽에 있던 쪽" 이다
--    가르는 것은 form_version 이고, 이 판에서 8 로 올린다.
--    지금 살아 있는 응답은 개발자 본인이 화면을 보려고 낸 한 건뿐이라
--    실제로 섞일 값이 없다.
--
-- 🔴 뷰를 다시 만든다. CREATE OR REPLACE VIEW 로는 **출력 칸 이름을 못 바꾼다**
--    (Postgres 가 거부한다). SELECT * 로 만든 뷰도 만들 때 칸 이름이 박히므로,
--    RENAME 만 하면 뷰는 계속 top_was 라는 이름으로 내보낸다.
--    그래서 DROP 하고 다시 만든다.
--
-- 🔴 두 번 돌려도 안 깨져야 한다. 배포가 두 번 돌 수 있다.
--
-- ── 🔴 이 SQL 이 실제로 어디까지 확인됐나 ────────────────────────────
-- 거짓으로 안심하지 않도록 적어 둔다. 2026-09-12, postgres:16-alpine —
-- **배포 대상과 같은 이미지**에서 돌렸다.
--
--   ① 빈 DB 에 옛 schema.sql(top_was) → 0008        종료 0
--   ② 같은 DB 에 0008 을 **두 번째로** 다시          종료 0 (두 번 돌려도 안 깨진다)
--   ③ 빈 DB 에 새 schema.sql 만
--   ④ ①과 ③의 표를 글자 단위로 대조                  **차이 0줄**
--      (information_schema.columns 168줄 — 칸 이름 · 순서 · 자료형 · NULL 허용 ·
--       기본값, 그리고 pg_constraint · pg_indexes · pg_views 전부)
--   ⑤ 값이 든 표에서도 — form_version 7 · top_was 1 인 행을 넣고 0008 을 돌린 뒤
--      left_was 가 **1 그대로** 남는 것을 확인. 이어서 form_version 8 · left_was 0
--      인 새 행도 들어간다
--   ⑥ 뷰 pairwise_choice_real 이 **left_was** 로 내보낸다
--   ⑦ 제약 이름이 pairwise_choice_left_was_ok 다
--
-- 🔴 **살아 있는 DB 에는 아직 안 돌렸다.** 위는 전부 빈 검사용 DB 다.
--
-- 돌리는 법:
--   docker exec -i survey-postgres psql -U survey -d survey -v ON_ERROR_STOP=1 \
--     < survey-recommend/migrations/0008_pairwise_left_was.sql

BEGIN;

-- ── ① 칸 이름 ────────────────────────────────────────────────────────
-- 🔴 RENAME COLUMN 에는 IF EXISTS 가 없다. 두 번 돌아도 안 깨지게 감싼다.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'pairwise_choice' AND column_name = 'top_was')
  THEN
    ALTER TABLE pairwise_choice RENAME COLUMN top_was TO left_was;
  END IF;
END $$;

-- ── ② 제약 이름 ──────────────────────────────────────────────────────
-- 제약은 이름을 바꿔도 내용이 안 바뀐다. 그래도 바꾼다 — 이름이 top 인 채로
-- 남으면 \d 로 표를 볼 때마다 "위" 라는 말이 다시 나온다.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pairwise_choice_top_was_ok')
  THEN
    ALTER TABLE pairwise_choice
      RENAME CONSTRAINT pairwise_choice_top_was_ok TO pairwise_choice_left_was_ok;
  END IF;
END $$;

-- ── ③ 뷰를 다시 만든다 ───────────────────────────────────────────────
-- 함정을 뺀 것만 본다. 추정에 쓰는 것은 이 뷰다.
DROP VIEW IF EXISTS pairwise_choice_real;
CREATE VIEW pairwise_choice_real AS
  SELECT * FROM pairwise_choice WHERE is_trap IS FALSE;

COMMIT;

-- ── 확인 ─────────────────────────────────────────────────────────────
-- 아래 셋이 다 나와야 한다.
--   \d pairwise_choice                     → left_was 가 있고 top_was 가 없다
--   \d pairwise_choice_real                → 같은 칸 이름이 뷰에도 있다
--   SELECT conname FROM pg_constraint
--     WHERE conrelid = 'pairwise_choice'::regclass AND conname LIKE '%was%';
--                                          → pairwise_choice_left_was_ok
