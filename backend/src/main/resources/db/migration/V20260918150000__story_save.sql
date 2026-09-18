-- 기록(글) 저장 — 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게".
--
-- saved_place(V20260915110000)와 같은 모양이다. ReactionType.java의 주석이 이미 이 자리를
-- 예고해 뒀다 — "나중에 「글 저장」이 생기면 그때 별도 표로 간다. 여기에 SAVE를 끼우면
-- 좋아요를 눌렀다가 저장으로 바꾸면 좋아요가 사라지는 이상한 동작이 된다".
--
-- 그래서 story_reaction과 합치지 않고 saved_place처럼 별도 표로 둔다 — 한 사람이 한 글에
-- 반응(좋아요/싫어요)과 저장을 동시에 가질 수 있어야 한다.

CREATE TABLE story_save (
    story_save_id UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL,
    story_id      UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_story_save_user
        FOREIGN KEY (user_id)  REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_save_story
        FOREIGN KEY (story_id) REFERENCES story (story_id)   ON DELETE CASCADE,

    -- 한 사람이 한 글을 두 번 저장할 수 없다 — saved_place의 uk_saved_place와 같은 이유다.
    -- 연타·재시도마다 행이 쌓이면 저장 목록에 같은 글이 여러 번 뜬다.
    CONSTRAINT uk_story_save UNIQUE (user_id, story_id)
);

-- 조회용 색인을 따로 만들지 않는다. "내가 저장한 것 전부"(user_id = ?) 질의는 위 UNIQUE
-- 제약이 만드는 색인을 앞 칸으로 그대로 받는다 — saved_place와 같은 근거다.

COMMENT ON TABLE story_save IS
    '사용자가 저장한(북마크) 기록. story_reaction(좋아요/싫어요)과는 별도 표 — 한 글에 반응과 저장을 동시에 가질 수 있다.';
