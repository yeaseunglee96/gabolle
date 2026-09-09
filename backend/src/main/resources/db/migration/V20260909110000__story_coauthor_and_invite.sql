-- S15P21E201-770 — 하나의 여행 기록을 여럿이 함께 쓴다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만드는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
-- story_coauthor   초대받아 그 기록을 함께 쓰게 된 사람. 만든 사람은 들어가지 않는다.
-- story_invite     "같이 쓰자" 링크 한 장. 표(token)가 곧 열쇠다.
-- story.last_edited_by   마지막으로 본문을 고친 사람.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 같은 사실을 두 곳에 적지 않는다 — 이 파일에서 제일 중요한 결정
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 참여자 표를 만들면서 거기에 만든 사람도 OWNER 로 한 줄 넣는 안이 먼저 떠오른다.
-- 그러면 "누가 만들었나" 가 story.author_user_id 와 story_coauthor 두 곳에 적힌다.
-- 둘이 어긋나는 순간(한쪽만 고치는 코드가 하나라도 생기면 어긋난다) 어느 쪽이 맞는지
-- 알 방법이 없고, 그걸 막으려면 "기록당 OWNER 는 정확히 하나" 같은 제약을 또 걸어야 한다.
--
-- 그래서 뜻을 나눈다.
--
-- story.author_user_id   **만든 사람.** 안 바뀐다. 공개 범위 변경·삭제·초대 발급의 기준.
-- story_coauthor         **나중에 합류한 사람.** 늘고 준다.
--
-- 둘은 서로 다른 사실이므로 중복이 아니다. 역할 칸도, OWNER 유일성 제약도 필요 없다.
-- "이 기록을 고칠 수 있는 사람" 은 (만든 사람) ∪ (story_coauthor) 로 그때 계산한다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 역할(role) 칸을 두지 않는 이유
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 여행 초대(trip_invite)에는 EDITOR·VIEWER 가 있다. 기록에는 두지 않는다.
-- 기록 초대의 뜻은 "같이 쓰자" 하나뿐이고, "보기만 해라" 는 이미 공개 범위(visibility)가
-- 담당한다. 안 쓰는 칸을 미리 만들면 나중에 그 칸의 뜻을 아무도 모른 채 값이 쌓인다.
-- 필요해지면 그때 더한다 — 표에 칸을 더하는 것은 쉽고, 뜻이 흐려진 칸을 되돌리는 것은 어렵다.

CREATE TABLE story_coauthor (
    story_id    UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    -- 누가 불러들였나. 만든 사람이 직접 넣었을 수도, 그가 만든 링크를 눌러 들어왔을 수도 있다.
    invited_by  UUID          NOT NULL,
    joined_at   TIMESTAMPTZ   NOT NULL,

    -- (기록, 사람) 한 쌍이 곧 열쇠다. 같은 링크를 두 번 눌러도 두 줄이 안 생긴다.
    -- 화면에서 두 번 눌리는 것을 막는 것으로는 부족하다 — 느린 통신에서 사용자는 반드시
    -- 두 번 누르고, 그때 두 요청이 거의 동시에 도착한다. 데이터베이스가 막아야 한다.
    CONSTRAINT pk_story_coauthor PRIMARY KEY (story_id, user_id),

    CONSTRAINT fk_story_coauthor_story FOREIGN KEY (story_id)
        REFERENCES story (story_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_coauthor_user FOREIGN KEY (user_id)
        REFERENCES app_user (user_id),
    CONSTRAINT fk_story_coauthor_inviter FOREIGN KEY (invited_by)
        REFERENCES app_user (user_id)
);

-- "내가 함께 쓰는 기록들" 을 사람 기준으로 찾는 질의가 생긴다(내 기록 목록).
CREATE INDEX ix_story_coauthor_user ON story_coauthor (user_id);

-- story 는 지울 때 행을 지우지 않고 deleted_at 을 찍는다. 그러니 위 CASCADE 는 평소에
-- 작동하지 않는다 — 계정 정리 배치가 행을 진짜로 지울 때만 쓸리라고 걸어 둔 그물이다.
-- "기록이 지워졌는데 참여자 줄만 남는" 상태를 만들지 않기 위한 것이지, 삭제 경로가
-- 이것에 기대는 것이 아니다.

CREATE TABLE story_invite (
    story_invite_id  UUID          PRIMARY KEY,
    story_id         UUID          NOT NULL,
    -- 링크에 실려 나가는 문자열. 이것을 아는 사람이 곧 초대받은 사람이다.
    token            VARCHAR(64)   NOT NULL,
    created_by       UUID          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    expires_at       TIMESTAMPTZ   NOT NULL,

    -- 표가 열쇠이므로 겹치면 남의 기록에 들어간다. 유일성은 애플리케이션이 아니라
    -- 여기서 보장한다.
    CONSTRAINT ux_story_invite_token UNIQUE (token),
    CONSTRAINT fk_story_invite_story FOREIGN KEY (story_id)
        REFERENCES story (story_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_invite_creator FOREIGN KEY (created_by)
        REFERENCES app_user (user_id),
    -- 만료가 생성보다 앞설 수 없다. 값이 뒤집힌 채 들어오면 그 링크는 만들자마자 죽은 것이라
    -- 사용자에게는 "왜 안 되는지 알 수 없는 링크" 가 된다.
    CONSTRAINT ck_story_invite_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_story_invite_story ON story_invite (story_id);

-- ══════════════════════════════════════════════════════════════════════════════
-- 마지막으로 고친 사람
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 동시에 고치면 나중에 저장한 쪽이 이긴다(팀 결정, 2026-09-09). 충돌을 감지해서 멈추지
-- 않는다 — 같이 여행한 사이라면 편하게 고칠 수 있어야 한다는 판단이다.
--
-- 그 대신 **누가 마지막에 고쳤는지는 남긴다.** 덮어쓰기에서 이 칸마저 없으면 사용자는
-- 자기 글이 왜 바뀌었는지 알 방법이 전혀 없다. 화면이 "방금 OO 님이 고쳤어요" 를
-- 보여줄 수 있는 최소한의 근거다.
--
-- 기존 기록에는 NULL 이다. 만든 사람으로 채우지 않는다 — 아무도 "고친" 적이 없기
-- 때문이다. 없는 사실을 그럴듯한 값으로 채우지 않는다.
ALTER TABLE story ADD COLUMN last_edited_by UUID;

ALTER TABLE story ADD CONSTRAINT fk_story_last_editor
    FOREIGN KEY (last_edited_by) REFERENCES app_user (user_id);
