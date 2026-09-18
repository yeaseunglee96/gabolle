-- 기록에 붙는 동영상 — S15P21E201-1275. 한 기록에 0개 또는 1개.
--
-- 🔴 왜 uploaded_image 에 같이 안 담나
--
-- 그 표의 도메인(UploadedImage)이 3MB 를 강제한다 — byteSize > MAX_BYTES 면 생성자가 거부한다.
-- 동영상을 거기 담으려면 그 검증을 풀어야 하고, 그러면 사진 길이 같이 바뀐다. 이름도
-- image_url·uploaded_image 라 거짓이 된다. 그래서 파일 표를 따로 둔다.
--
-- 🔴 썸네일은 반대로 uploaded_image 그대로다
--
-- 썸네일은 3MB 안쪽 JPEG 이고 기존 사진 창구(POST /api/v1/uploads/story-image)로 올린다 —
-- ImageSniffer·ImageSanitizer(촬영 위치 제거)가 그대로 걸린다. 「길은 공유하되 자리는 따로」다.
-- 그 덕에 탈퇴 삭제가 썸네일을 이미 덮는다(uploaded_image 를 올린 사람으로 지우므로).
--
-- 🔴 썸네일이 story_image 에 안 들어가는 이유
--
-- 들어가면 「한 기록에 사진 3장」(ck_story_image_position) 한 칸을 먹는다. 동영상을 넣으면
-- 사진이 2장만 들어가는 셈인데 사용자에게 설명할 수 없다. 썸네일은 사진이 아니라 동영상의
-- 일부라 순서(position)를 가질 이유도 없다.

CREATE TABLE uploaded_video (
    uploaded_video_id UUID          PRIMARY KEY,
    uploader_user_id  UUID          NOT NULL,
    storage_key       VARCHAR(300)  NOT NULL,
    video_url         VARCHAR(500)  NOT NULL,
    content_type      VARCHAR(50)   NOT NULL,
    -- 🔴 BIGINT 다. uploaded_image.byte_size 는 INTEGER 인데 그쪽은 3MB 상한이 있어서 안전했다.
    --    여기 상한은 설정값이라 코드를 안 고치고 올릴 수 있다 — 표가 먼저 막으면 안 된다.
    byte_size         BIGINT        NOT NULL,
    -- 앱이 재서 보낸 길이. 서버는 동영상 파일을 열지 않으므로 직접 재지 않는다(2026-09-18 결정:
    -- 줄이기·썸네일·길이 재기를 전부 앱이 한다. 서버에 디코더를 들이지 않는다). 못 받으면 NULL.
    duration_sec      INTEGER,
    created_at        TIMESTAMPTZ   NOT NULL,
    -- 저장소에서 파일을 지운 시각. uploaded_image 와 같은 뜻이다.
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT fk_uploaded_video_user
        FOREIGN KEY (uploader_user_id) REFERENCES app_user (user_id),
    CONSTRAINT uq_uploaded_video_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_uploaded_video_byte_size   CHECK (byte_size > 0),
    CONSTRAINT ck_uploaded_video_duration    CHECK (duration_sec IS NULL OR duration_sec > 0)
);

-- 탈퇴가 "이 사람이 올린 것" 으로 전부 찾는다(AccountDeletionService). uploaded_image 에는
-- 이 색인이 없지만 그건 그 표가 story_image 를 거쳐서도 닿기 때문이고, 여기는 이 길뿐이다.
CREATE INDEX ix_uploaded_video_uploader ON uploaded_video (uploader_user_id);

COMMENT ON TABLE uploaded_video IS
    '저장소에 올라간 동영상 한 개. 파일 내용은 여기 없고 주소와 저장 키만 있다. uploaded_image 와 같은 모양이되 크기 상한이 다르다.';

CREATE TABLE story_video (
    story_video_id      UUID        PRIMARY KEY,
    story_id            UUID        NOT NULL,
    uploaded_video_id   UUID        NOT NULL,
    -- 🔴 썸네일 파일의 업로드 id. 이 칸이 없으면 기록을 지울 때 썸네일이 샌다 —
    --    StoryService.delete 는 "기록에 붙은 파일" 을 표로 찾는다. 표에 없는 파일은
    --    지우는 길이 아예 없고, 기록에 안 붙은 업로드를 쓸어 가는 배치도 이 저장소에 없다.
    thumbnail_upload_id UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,

    -- story_image 와 같게 맞춘다. 기록은 소프트 삭제(deleted_at)라 이 CASCADE 는 실제로는
    -- 안 터지고, 파일을 지우는 것은 언제나 코드다. 하드 삭제가 들어오는 날을 위한 것이다.
    CONSTRAINT fk_story_video_story
        FOREIGN KEY (story_id) REFERENCES story (story_id) ON DELETE CASCADE,
    -- 🔴 업로드 쪽에는 ON DELETE 를 두지 않는다 — story_image 와 같다.
    --    이것이 탈퇴에서 순서를 강제한다: story_video 를 먼저 지우지 않고
    --    uploaded_video/uploaded_image 를 지우면 외래키 위반으로 탈퇴 전체가 실패한다.
    --    조용히 통과하는 것보다 낫다.
    CONSTRAINT fk_story_video_upload
        FOREIGN KEY (uploaded_video_id)   REFERENCES uploaded_video (uploaded_video_id),
    CONSTRAINT fk_story_video_thumbnail
        FOREIGN KEY (thumbnail_upload_id) REFERENCES uploaded_image (uploaded_image_id),

    -- 한 기록에 동영상은 0개 또는 1개. 시안이 「동영상 0/1」로 정했다.
    CONSTRAINT uq_story_video_story     UNIQUE (story_id),
    -- 한 파일은 한 기록에만 붙는다 — uq_story_image_upload 와 같은 이유다.
    -- 두 기록이 나눠 쓰면 한쪽을 지울 때 파일이 사라져 다른 쪽이 깨진다.
    CONSTRAINT uq_story_video_upload    UNIQUE (uploaded_video_id),
    CONSTRAINT uq_story_video_thumbnail UNIQUE (thumbnail_upload_id)
);

COMMENT ON TABLE story_video IS
    '기록에 붙은 동영상. 재생 파일(uploaded_video)과 썸네일 파일(uploaded_image)의 id 를 둘 다 들고 있다 — 기록을 지울 때 둘 다 지우기 위해서다.';
