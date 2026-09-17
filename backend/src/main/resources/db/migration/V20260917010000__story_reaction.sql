-- 기록(글)에 좋아요·싫어요를 단다.
--
-- 지금 커뮤니티에는 사람의 반응이 한 칸도 없다. story 표에 좋아요 칸이 없고, 반응 표도
-- 없고, backend 전체에서 STORY_LIKE·reaction 이 한 건도 안 나온다. 사람 사이 신호로
-- 있는 것은 user_follow 하나뿐이다. 그래서 커뮤니티 목록은 최신순으로만 설 수 있다
-- (StoryFeedService.feed 가 publish_at 커서로 자른다).
--
-- 🔴 하트는 좋아요의 다른 이름이다 — 표가 하나인 이유
--
--    장소 쪽은 감정(PLACE_LIKE·PLACE_DISLIKE)과 저장(saved_place 하트)이 따로다.
--    글에는 「나중에 보려고 저장」이라는 개념이 없고, 화면의 하트 아이콘이 곧 좋아요다.
--    그래서 여기서는 표 하나에 종류 둘이다. 나중에 「글 저장」이 생기면 그때 별도 표로
--    간다 — saved_place 가 recommendation_place_action 과 갈라져 있는 것과 같은 이유다.

CREATE TABLE story_reaction (
    story_id   UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    reaction   VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- 🔴 한 사람이 한 글에 하나만. 좋아요를 눌렀다가 싫어요로 바꾸면 행이 바뀌지 늘지
    --    않는다. saved_place 의 uk_saved_place 와 같은 판단이다 — 제약이 없으면 연타나
    --    재시도마다 행이 쌓이고, 인기순 집계가 그만큼 부푼다.
    CONSTRAINT pk_story_reaction PRIMARY KEY (story_id, user_id),

    CONSTRAINT fk_story_reaction_story
        FOREIGN KEY (story_id) REFERENCES story (story_id)    ON DELETE CASCADE,
    CONSTRAINT fk_story_reaction_user
        FOREIGN KEY (user_id)  REFERENCES app_user (user_id)  ON DELETE CASCADE,

    -- 🔴 값을 DB 가 막는다. 자바 열거형만 믿으면 다른 경로(적재 스크립트·손으로 넣는
    --    SQL)로 오타가 들어오고, 그 행은 집계에서 조용히 빠지거나 조용히 섞인다.
    CONSTRAINT ck_story_reaction_value
        CHECK (reaction IN ('LIKE', 'DISLIKE'))
);

-- 🔴 인기순이 쓸 색인이다 — "최근 24시간 안에 이 글이 받은 좋아요".
--
--    PK 가 (story_id, user_id) 라 story_id 로는 이미 빠르지만, 인기순 질의는
--    "시각으로 먼저 자르고 글별로 센다" 라 시각이 앞에 와야 한다. PK 색인으로는
--    24시간 조건이 전체 훑기가 된다.
--
--    reaction 을 포함시켜 좋아요만 세는 질의가 표를 안 읽고 색인만으로 끝나게 한다.
CREATE INDEX ix_story_reaction_recent
    ON story_reaction (created_at DESC, reaction, story_id);

COMMENT ON TABLE story_reaction IS
    '기록(글)에 대한 사람의 반응. 한 사람이 한 글에 하나만 — 좋아요를 싫어요로 바꾸면 행이 바뀐다. 하트는 좋아요의 다른 이름이라 저장 개념은 여기 없다.';

COMMENT ON COLUMN story_reaction.reaction IS
    'LIKE · DISLIKE. 화면의 하트가 LIKE 다.';
