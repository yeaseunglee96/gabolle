-- S15P21E201-207 · -216 · -370 · -221 · -226 · -233 · -242 — 여행 기록(story)·사진·팔로우.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만드는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
--   story                  사진·글로 남기는 여행 기록 한 건. 위치는 "지역" 문자열 하나만 둔다.
--   uploaded_image         파일 저장소에 올라간 사진 한 장. 표에는 주소와 저장 키만 있다.
--   story_image            기록에 붙은 사진(최대 3장). uploaded_image 를 가리킨다.
--   user_follow            누가 누구를 팔로우하나. 한 쌍은 한 줄.
--   storage_cleanup_queue  저장소에서 못 지운 파일의 목록. 뒷정리 작업이 비운다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 🔴 여기서 지키는 약속 셋
-- ══════════════════════════════════════════════════════════════════════════════
--
-- (1) 사진은 데이터베이스에 넣지 않는다. 이 파일 어디에도 BYTEA 나 base64 텍스트 칸이 없다.
--     사진 자체는 파일 저장소에 있고 표에는 주소(image_url)와 저장 키(storage_key)만 있다.
--     참고 코드처럼 글자로 넣으면 3MB 사진이 4MB 가 되고, 목록을 읽을 때마다 그 덩어리가
--     서버 메모리를 지나간다 — 사용자가 늘면 데이터베이스가 먼저 죽는다(S15P21E201-174).
--
-- (2) 좌표는 저장하지 않는다. story 에 lat·lng 칸이 없다. "위치는 지역 단위로만" 이라는
--     화면 문구를 서버가 실제로 지키는 자리다(S15P21E201-207). 칸이 없으면 실수로도 못 넣는다.
--
-- (3) 공개 시각(publish_at)이 지난 기록만 피드에 나온다. 기본값은 여행이 끝난 뒤다 —
--     여행 중인 사람의 현재 위치가 기록으로 새지 않게(기획서 5.3 D-5).
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만들지 않는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 좋아요·댓글·신고 표는 없다. 신고(-254·-267)와 방문 인증(-279·-287)은 그 티켓이 자기
-- 마이그레이션으로 더한다. V20260905140000 의 community_feed.post_id 가 가리킬 표가
-- 이 story 다 — FK 는 그쪽 담당(-580)이 필요할 때 건다. 여기서 남의 표를 고치지 않는다.

CREATE TABLE uploaded_image (
    uploaded_image_id  UUID          PRIMARY KEY,
    uploader_user_id   UUID          NOT NULL,
    -- 저장소 안의 위치. 지울 때 이 값으로 지운다. 주소(image_url)는 바뀔 수 있지만 키는 안 바뀐다.
    storage_key        VARCHAR(300)  NOT NULL,
    image_url          VARCHAR(500)  NOT NULL,
    content_type       VARCHAR(50)   NOT NULL,
    byte_size          INTEGER       NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    -- 저장소에서 지운 시각. 기록을 지울 때 함께 찍힌다. NULL 이면 파일이 살아 있다.
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT fk_uploaded_image_uploader FOREIGN KEY (uploader_user_id) REFERENCES app_user (user_id),
    CONSTRAINT uq_uploaded_image_key UNIQUE (storage_key),
    CONSTRAINT ck_uploaded_image_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    -- 한 장 3MB(S15P21E201-216). 애플리케이션이 먼저 거절하지만 DB 도 같은 선을 안다.
    CONSTRAINT ck_uploaded_image_size CHECK (byte_size > 0 AND byte_size <= 3145728)
);

CREATE INDEX ix_uploaded_image_uploader ON uploaded_image (uploader_user_id, created_at DESC);

COMMENT ON TABLE uploaded_image IS
    '파일 저장소에 올라간 사진 한 장. 사진 데이터는 여기 없다 — 주소와 저장 키만 있다. 기록에 붙기 전까지는 올린 사람만 쓸 수 있다.';

CREATE TABLE story (
    story_id         UUID          PRIMARY KEY,
    author_user_id   UUID          NOT NULL,
    -- 어느 여행에서 남긴 기록인가. 없을 수 있다(여행 없이 남기는 기록). 있으면 공개 시각 기본값의 근거다.
    trip_id          UUID,
    -- 연결한 장소. 없을 수 있다.
    place_id         UUID,
    body             TEXT          NOT NULL,
    -- "해운대구" 처럼 지역 단위 문자열. 좌표 칸은 이 표에 없다 — 위 약속 (2).
    region           VARCHAR(100),
    visibility       VARCHAR(20)   NOT NULL,
    -- 이 시각이 지나야 피드에 나온다. 기본값은 여행 종료 다음 날 0시(여행 시간대)다.
    publish_at       TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    -- 삭제는 행을 지우지 않고 시각을 찍는다(app_user 와 같은 판단). 피드·조회는 NULL 만 본다.
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT fk_story_author FOREIGN KEY (author_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_story_trip   FOREIGN KEY (trip_id)  REFERENCES trip (trip_id) ON DELETE SET NULL,
    CONSTRAINT fk_story_place  FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE SET NULL,
    CONSTRAINT ck_story_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
    -- 글 최대 500자(S15P21E201-114). char_length 는 글자 수다 — 한글 한 자가 한 자.
    CONSTRAINT ck_story_body_length CHECK (char_length(body) <= 500)
);

-- 🔴 피드의 핵심 색인. 커서는 (publish_at, story_id) 쌍이라 정렬도 그 쌍으로 한다.
--    같은 시각에 올라온 기록이 둘 이상이어도 story_id 가 순서를 확정하므로 건너뛰거나
--    두 번 나오는 일이 없다. 지운 기록은 색인에서 빼서 색인을 작게 유지한다.
CREATE INDEX ix_story_feed
    ON story (publish_at DESC, story_id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_story_author
    ON story (author_user_id, publish_at DESC, story_id DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN story.region IS
    '지역 단위 위치 문자열. 좌표는 받아도 저장하지 않는다 — 이 표에 lat·lng 칸이 없는 것이 그 약속의 구현이다.';
COMMENT ON COLUMN story.publish_at IS
    '이 시각이 지나야 피드에 나온다. 요청이 안 주면 여행 종료 다음 날 0시(여행 시간대), 여행이 없으면 지금이다.';

CREATE TABLE story_image (
    story_image_id     UUID          PRIMARY KEY,
    story_id           UUID          NOT NULL,
    uploaded_image_id  UUID          NOT NULL,
    -- 1부터 3까지. 화면에 보이는 순서다.
    position           SMALLINT      NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_story_image_story FOREIGN KEY (story_id) REFERENCES story (story_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_image_upload FOREIGN KEY (uploaded_image_id) REFERENCES uploaded_image (uploaded_image_id),
    CONSTRAINT uq_story_image_position UNIQUE (story_id, position),
    -- 한 사진은 한 기록에만 붙는다. 같은 업로드를 두 기록이 나눠 쓰면 한쪽을 지울 때 파일이 사라져 다른 쪽이 깨진다.
    CONSTRAINT uq_story_image_upload UNIQUE (uploaded_image_id),
    CONSTRAINT ck_story_image_position CHECK (position BETWEEN 1 AND 3)
);

CREATE TABLE user_follow (
    follower_user_id  UUID         NOT NULL,
    followee_user_id  UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,

    -- 🔴 한 쌍은 한 줄. 버튼을 빠르게 두 번 눌러도 두 줄이 생기지 않는 것은 화면이 아니라 이 제약이 보장한다(S15P21E201-242).
    CONSTRAINT pk_user_follow PRIMARY KEY (follower_user_id, followee_user_id),
    CONSTRAINT fk_user_follow_follower FOREIGN KEY (follower_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_follow_followee FOREIGN KEY (followee_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    -- 자기 자신은 팔로우할 수 없다.
    CONSTRAINT ck_user_follow_not_self CHECK (follower_user_id <> followee_user_id)
);

-- 팔로잉 피드는 "내가 팔로우한 사람들" 을 follower 로 찾고, 프로필의 팔로워 수는 followee 로 센다.
CREATE INDEX ix_user_follow_followee ON user_follow (followee_user_id);

CREATE TABLE storage_cleanup_queue (
    storage_key      VARCHAR(300)  PRIMARY KEY,
    -- 왜 지우려 했나. STORY_DELETED | ACCOUNT_DELETED | ORPHAN_UPLOAD
    reason           VARCHAR(50)   NOT NULL,
    first_failed_at  TIMESTAMPTZ   NOT NULL,
    last_attempt_at  TIMESTAMPTZ   NOT NULL,
    attempts         INTEGER       NOT NULL DEFAULT 1,
    last_error       TEXT
);

COMMENT ON TABLE storage_cleanup_queue IS
    '저장소에서 지우기에 실패한 파일 목록(S15P21E201-226·-426). 파일 하나를 못 지웠다고 기록 삭제·탈퇴를 되돌리지 않는다 — 대신 여기 남기고 뒷정리 작업이 다시 지운다. 다 지워지면 행이 빠진다.';
