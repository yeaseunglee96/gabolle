-- S15P21E201-1840 — 장소 한 곳의 사진 여러 장.
--
-- 🔴 왜
--    place 행에는 대표 사진이 한 장뿐이다. 부산관광아카이브(공공누리 제1유형)에는 같은 장소를 찍은 사진이
--    여러 장 있고(5장 이상인 곳 470곳, 10장 이상 255곳 — 2026-09-29 수집분), 여행객은 상세 화면에서 여러 장을
--    넘겨 보고 싶어 한다. 대표 사진 칸(place.photo_url)은 그대로 두고, 그 밖의 사진만 이 표에 쌓는다.
--
-- 🔴 무엇을 싣나
--    출처만 밝히면 영구히 저장·사용할 수 있는 사진만 — 공공누리 제1유형 등. 사진 파일은 우리 MinIO 에 복사해 두고
--    (url 은 /photos/... 주소) 원본 페이지는 file_page 에 남긴다. Google 사진은 저장이 금지라 여기 넣지 않는다.
--
-- 🔴 place 를 가리키는 외래키를 일부러 걸지 않는다
--    · 걸면 장소 합치기(place_merge)가 옮겨야 하는 표 목록이 늘어 그 함수를 다시 써야 한다.
--    · 합쳐진(MERGED) 장소는 화면에 안 나오므로 그 사진은 보이지 않을 뿐 해가 없다. 장소를 지우는 경로는 없다.
--
-- 🔴 버전 V20260929200000 — back/dev 의 가장 큰 번호(V20260929120200)보다 위로 잡았다.

CREATE TABLE place_photo (
    photo_id      BIGSERIAL     PRIMARY KEY,
    place_id      UUID          NOT NULL,
    position      INTEGER       NOT NULL,
    url           VARCHAR(500)  NOT NULL,
    source        VARCHAR(100)  NOT NULL,
    license_name  VARCHAR(100),
    license_url   VARCHAR(500),
    file_page     VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_place_photo_position CHECK (position >= 1),
    -- 앱이 그대로 여는 주소라 평문 http 는 받지 않는다(안드로이드·iOS·웹 모두 막는다 — PhotoUrlScheme).
    CONSTRAINT ck_place_photo_https CHECK (url LIKE 'https://%'),
    CONSTRAINT uq_place_photo_url UNIQUE (place_id, url),
    CONSTRAINT uq_place_photo_position UNIQUE (place_id, position)
);
