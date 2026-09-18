-- ══════════════════════════════════════════════════════════════════════════════
-- 글의 조회수와 인용수 — S15P21E201-1201
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 지금 글에는 사람이 몇 번 봤는지가 한 칸도 없다. 반응(story_reaction)은
-- S15P21E201-1173 이 넣었지만, 「눌러서 읽었다」와 「좋아요를 눌렀다」는 다른 신호다.
--
-- 규칙은 사장님이 정한 것이고 여기서 다시 논의하지 않는다.
--
--   중복        사람 × 글 × 하루 한 번
--   작성자 본인  안 센다
--   비회원      센다 — 익명 세션(anonymous_session)으로 식별
--   낱개 보관    90일, 그 뒤엔 누적 칸만
--
-- ── 🔴 이름이 link_copy 인 이유 ───────────────────────────────────────────────
--
-- 화면에서 부르는 말은 「인용수」지만 표와 칸 이름은 link_copy 다.
-- 우리가 실제로 아는 것은 「복사 버튼을 눌렀다」뿐이다. 그것을 quote 라고 적으면
-- 표가 우리가 모르는 것을 주장하게 된다 — 복사한 사람이 인용했는지, 어디에 붙였는지,
-- 붙이긴 했는지 우리는 모른다.
--
-- 🔴 칸 이름만 그렇게 하고 표 이름을 story_quote 로 두는 안도 있었는데, 그러면
--    칸에서 쫓아낸 낱말이 표 이름으로 돌아온다. 표 이름이 칸보다 오래 가고 고치기
--    더 어렵다. 그래서 둘 다 link_copy 다.
--
-- ── 🔴 사람이 두 종류다 — 칸 둘과 CHECK ──────────────────────────────────────
--
-- 「사람 × 글 × 하루 한 번」인데 사람이 회원(app_user)과 익명 세션(anonymous_session)
-- 둘이다. 이 저장소에 둘을 한 표에서 식별하는 선례가 없어 두 방식을 견주었다.
--
--   (가) 칸 하나 viewer_key 문자열  — 간단하지만 외래키를 못 건다
--   (나) 칸 둘 + CHECK 로 하나만    ← 이것을 골랐다
--
-- (나) 인 이유는 탈퇴다. AccountDeletionService 는 user_id 로 지운다. 문자열 열쇠면
-- 그 목록에 넣을 수 없고, 고아 행이 생겨도 DB 가 못 막는다.
--
-- 🔴 CHECK 는 「둘 중 하나」가 아니라 「정확히 하나」다. 둘 다 비면 누가 봤는지 모르는
--    행이 되고, 둘 다 차면 같은 조회가 두 사람으로 세어진다.
--
-- ── 🔴 하루의 경계는 앱이 정한다 ─────────────────────────────────────────────
--
-- viewed_on 을 DB 의 current_date 로 채우지 않는다. 그 값은 서버의 시간대를 따르는데,
-- 이 서비스는 부산 여행이라 사용자의 하루는 KST 다. UTC 를 쓰면 한국 시각 오전 9시에
-- 날짜가 바뀌어 「어제 본 글을 오늘 또 봐도 안 세는」 구간이 생긴다.
-- 그래서 앱이 KST 로 계산해 넣는다 — 시간대를 코드 한 곳에서만 정하려는 것이다.
--
-- ── 🔴 CASCADE 가 걸려 있지만 그것만 믿으면 안 된다 ─────────────────────────
--
-- app_user 에 ON DELETE CASCADE 를 걸었다(story_reaction 과 같은 모양). 그런데
-- AccountDeletionService 는 계정 행을 지우지 않고 익명화하므로 그 CASCADE 는
-- 한 번도 안 터진다. 그래서 두 표를 USER_OWNED_ROWS 에 처음부터 넣는다 —
-- S15P21E201-1157 이 표 열 개를 뒤늦게 메운 자리이고, 같은 일을 반복하지 않는다.
--
-- 🔴 AccountDeletionTableInventoryTest 가 이 마이그레이션 때문에 빨개진다. 의도한
--    것이다 — 그 검사는 「app_user 를 가리키는 표가 새로 생겼으니 탈퇴 때 어떻게 할지
--    정하라」고 묻는다. 이 MR 이 그 답(USER_OWNED_ROWS 에 넣는다)과 함께 목록도 고친다.

ALTER TABLE story ADD COLUMN view_count      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE story ADD COLUMN link_copy_count INTEGER NOT NULL DEFAULT 0;

-- 🔴 음수를 DB 가 막는다. 낱개 행을 90일 뒤에 지울 때 누적 칸을 같이 내리는 실수가
--    생기면(내리면 안 된다 — 누적은 누적이다) 조용히 -1 이 된다.
--    reply_count 가 같은 이유로 같은 제약을 갖고 있다.
ALTER TABLE story ADD CONSTRAINT ck_story_view_count      CHECK (view_count >= 0);
ALTER TABLE story ADD CONSTRAINT ck_story_link_copy_count CHECK (link_copy_count >= 0);

COMMENT ON COLUMN story.view_count IS
    '글을 눌러서 연 횟수. 사람 × 하루 한 번이고 작성자 본인은 안 센다. 노출 수가 아니다 (S15P21E201-1201)';
COMMENT ON COLUMN story.link_copy_count IS
    '공유 링크 복사 버튼을 누른 횟수. 화면에서는 인용수라고 부르지만 우리가 아는 것은 복사뿐이다 (S15P21E201-1201)';

-- ── 조회 낱개 ────────────────────────────────────────────────────────────────

CREATE TABLE story_view (
    story_view_id        UUID        PRIMARY KEY,
    story_id             UUID        NOT NULL,
    user_id              UUID,
    anonymous_session_id UUID,
    viewed_on            DATE        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_story_view_story FOREIGN KEY (story_id)
        REFERENCES story (story_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_view_user FOREIGN KEY (user_id)
        REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_view_anonymous FOREIGN KEY (anonymous_session_id)
        REFERENCES anonymous_session (session_id) ON DELETE CASCADE,
    CONSTRAINT ck_story_view_viewer
        CHECK ((user_id IS NOT NULL) <> (anonymous_session_id IS NOT NULL))
);

-- 🔴 하루 한 번을 DB 가 지킨다. 응용이 "오늘 것이 있나" 를 먼저 읽고 없으면 넣는 모양은
--    같은 사람이 두 기기에서 동시에 열면 둘 다 통과한다. 조건부 유일 색인이 그 경주를
--    끝에서 막고, 응용은 중복 키를 "이미 셌다" 로 읽으면 된다.
--
--    색인이 둘인 이유는 NULL 때문이다. 한 색인에 (story_id, user_id, anonymous_session_id,
--    viewed_on) 을 묶으면 NULL 이 서로 다른 값으로 취급돼 유일성이 안 걸린다.
CREATE UNIQUE INDEX ux_story_view_member
    ON story_view (story_id, user_id, viewed_on) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_story_view_anonymous
    ON story_view (story_id, anonymous_session_id, viewed_on) WHERE anonymous_session_id IS NOT NULL;

-- 90일 지난 낱개를 쓸어 갈 때 쓰는 색인. 그 배치가 없으면 이 표만 끝없이 자란다.
CREATE INDEX ix_story_view_viewed_on ON story_view (viewed_on);

COMMENT ON TABLE story_view IS
    '조회 낱개. 90일만 보관하고 그 뒤에는 story.view_count 누적 칸만 남는다 (S15P21E201-1201)';
COMMENT ON COLUMN story_view.viewed_on IS
    'KST 기준 날짜. 앱이 계산해 넣는다 — DB 의 current_date 를 쓰면 서버 시간대에 따라 하루 경계가 밀린다';

-- ── 링크 복사 낱개 ───────────────────────────────────────────────────────────
--
-- 조회와 같은 모양이다. 표를 하나로 합쳐 종류 칸을 두는 안도 있었지만 나누었다 —
-- 보관 기간이나 세는 규칙이 갈릴 때 합쳐 둔 표는 한쪽 규칙을 다른 쪽에 강요한다.
-- story_reaction 이 좋아요·싫어요를 한 표에 둔 것과는 다른 판단인데, 그쪽은 둘이
-- 같은 질문의 두 답이고 이쪽은 서로 다른 행동이다.

CREATE TABLE story_link_copy (
    story_link_copy_id   UUID        PRIMARY KEY,
    story_id             UUID        NOT NULL,
    user_id              UUID,
    anonymous_session_id UUID,
    copied_on            DATE        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_story_link_copy_story FOREIGN KEY (story_id)
        REFERENCES story (story_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_link_copy_user FOREIGN KEY (user_id)
        REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_link_copy_anonymous FOREIGN KEY (anonymous_session_id)
        REFERENCES anonymous_session (session_id) ON DELETE CASCADE,
    CONSTRAINT ck_story_link_copy_actor
        CHECK ((user_id IS NOT NULL) <> (anonymous_session_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_story_link_copy_member
    ON story_link_copy (story_id, user_id, copied_on) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_story_link_copy_anonymous
    ON story_link_copy (story_id, anonymous_session_id, copied_on) WHERE anonymous_session_id IS NOT NULL;

CREATE INDEX ix_story_link_copy_copied_on ON story_link_copy (copied_on);

COMMENT ON TABLE story_link_copy IS
    '링크 복사 낱개. 화면에서는 인용수라고 부르지만 우리가 아는 것은 복사뿐이다 (S15P21E201-1201)';

-- ── 되돌리기 ─────────────────────────────────────────────────────────────────
--
-- DROP TABLE story_link_copy;
-- DROP TABLE story_view;
-- ALTER TABLE story DROP COLUMN link_copy_count;
-- ALTER TABLE story DROP COLUMN view_count;
--
-- 🔴 되돌리면 누적 칸까지 사라진다. 낱개는 90일만 남으므로 그보다 오래된 조회는
--    누적 칸에만 있고 다시 만들 수 없다. 되돌리기 전에 그것을 알고 해야 한다.
