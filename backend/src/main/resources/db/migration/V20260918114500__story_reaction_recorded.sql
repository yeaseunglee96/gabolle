-- 글 반응의 결함 둘을 고친다 — S15P21E201-1173 후속.
--
-- 둘 다 한 뿌리다: 「언제 눌렸나」와 「이 사람이 이 글에 처음 손댄 때가 언제냐」를
-- created_at 한 칸으로 같이 쓰고 있었다.
--
-- 🔴 ① 24시간 인기 집계가 오늘 눌린 좋아요를 놓쳤다
--
--    upsert 의 DO UPDATE 가 created_at 을 일부러 안 건드린다. 그래서
--    countRecentLikes 의 created_at >= :since 가 자르는 것은 「지금 눌린 좋아요」가
--    아니라 「이 사람이 이 글에 처음 반응한 시각」이었다. 사흘 전에 싫어요를 눌렀던
--    사람이 오늘 좋아요로 바꾸면 행은 LIKE 인데 시각이 사흘 전이라 집계에서 빠진다.
--    진짜 PostgreSQL 로 재 봤다 — 현재 반응=LIKE, 24시간 집계=0.
--
--    reacted_at 을 따로 둔다. created_at 은 처음 손댄 때로 남고, reacted_at 은
--    지금의 반응을 고른 때다. 인기순은 reacted_at 을 읽는다.
--
--    🔴 updated_at 을 그냥 쓰지 않는 이유: 지금은 두 칸이 같은 순간에 움직이지만,
--    나중에 이 표에 다른 바뀌는 칸이 하나만 생겨도 updated_at 의 뜻이 조용히
--    달라지고 인기순이 그만큼 틀어진다. 그때 아무 검사도 안 빨개진다.
--
-- 🔴 ② 껐다 켰다를 반복하면 이벤트가 무제한으로 쌓였다
--
--    취소가 행을 지우므로 다음 좋아요는 언제나 「새로 넣은 것」이 되어 이벤트를
--    남겼다. 한 사람이 하트를 5번 껐다 켜면 event_outbox 에 story_like 5건이
--    쌓이고 표의 행은 0개다(이것도 실측했다). 이 기능이 막겠다고 적어 둔
--    「재시도가 신호를 부풀리면 인기순이 그만큼 틀린다」가 다른 문으로 그대로 났다.
--
--    고치는 방법 둘: 취소해도 행을 남기고(reaction = NULL), 이 사람이 이 글에
--    그 종류를 이미 남겼는지를 칸으로 들고 있는다. 그러면 이벤트가
--    (글, 사람, 종류)당 최대 하나로 묶인다.
--
--    🔴 「좋아요를 눌렀다」는 되풀이되는 사건이 아니라 사실이다. 같은 사람이 같은
--    글을 두 번 좋아한다는 것은 뜻이 없다 — 그래서 상한이 1이 맞다.

ALTER TABLE story_reaction
    -- 지금의 반응을 고른 때. 기존 행은 처음 누른 때가 곧 그때다.
    ADD COLUMN reacted_at TIMESTAMPTZ,
    -- 이 사람이 이 글에 이 종류를 한 번이라도 남겼나. 취소해도 안 내려간다 —
    -- 내려가면 껐다 켜는 것으로 이벤트를 다시 만들 수 있고, 그게 고치려는 결함이다.
    ADD COLUMN like_recorded    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dislike_recorded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE story_reaction SET reacted_at = created_at WHERE reacted_at IS NULL;
UPDATE story_reaction SET like_recorded    = TRUE WHERE reaction = 'LIKE';
UPDATE story_reaction SET dislike_recorded = TRUE WHERE reaction = 'DISLIKE';

ALTER TABLE story_reaction ALTER COLUMN reacted_at SET NOT NULL;

-- 🔴 기본값을 준다. 이 표에 넣는 곳이 앱만이 아니다 — 탈퇴 검사
--    (AccountDeletionOwnedRowsRemovedTest)처럼 SQL 로 직접 한 줄 심는 자리가 이미 있고,
--    NOT NULL 만 걸고 기본값을 안 주면 그 자리들이 전부 깨진다. 실제로 깨졌다.
--
--    기본값이 뜻을 흐리지도 않는다 — 「지금의 반응을 고른 때」의 기본값은 넣은 때가 맞다.
--    앱은 upsert 에서 언제나 값을 명시하므로 이 기본값에 기대지 않는다.
ALTER TABLE story_reaction ALTER COLUMN reacted_at SET DEFAULT now();

-- 🔴 취소가 행을 지우는 대신 이 칸을 비운다. 그래서 NULL 이 허용돼야 한다.
--    행이 남아야 위의 두 칸이 살아남고, 그래야 껐다 켠 것과 처음 누른 것을 가른다.
ALTER TABLE story_reaction ALTER COLUMN reaction DROP NOT NULL;

ALTER TABLE story_reaction DROP CONSTRAINT ck_story_reaction_value;
ALTER TABLE story_reaction ADD CONSTRAINT ck_story_reaction_value
    CHECK (reaction IS NULL OR reaction IN ('LIKE', 'DISLIKE'));

-- 🔴 색인도 reacted_at 으로 옮긴다. 안 옮기면 질의는 고쳐졌는데 색인이 딴 칸을
--    가리켜, 맞는 답을 느리게 내는 상태가 된다.
DROP INDEX ix_story_reaction_recent;
CREATE INDEX ix_story_reaction_recent
    ON story_reaction (reacted_at DESC, reaction, story_id)
    WHERE reaction IS NOT NULL;

COMMENT ON COLUMN story_reaction.reaction IS
    'LIKE · DISLIKE · NULL(취소함). 취소해도 행은 남는다 — like_recorded 를 지키기 위해서다.';
COMMENT ON COLUMN story_reaction.reacted_at IS
    '지금의 반응을 고른 때. 인기순의 24시간 창이 이 칸을 자른다(created_at 이 아니다).';
COMMENT ON COLUMN story_reaction.like_recorded IS
    '이 사람이 이 글에 좋아요를 한 번이라도 남겼나. 취소해도 안 내려간다 — 이벤트를 (글,사람,종류)당 하나로 묶는 칸이다.';
