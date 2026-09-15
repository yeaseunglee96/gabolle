-- S15P21E201-1013 — 컬렉션을 서버에 남긴다.
--
-- 지금은 기기(AsyncStorage)에만 있다. 화면도 「이 기기에만 저장돼요. 앱을 지우면
-- 사라져요」라고 정직하게 말하고 있다.
--
-- 🔴 항목은 두 종류다 — 제품 결정(2026-09-16). 둘 다 받는다.
--
--    (1) 장소 표를 가리킨다      우리가 아는 곳
--    (2) 사용자가 직접 적었다    이름·메모·사진을 손으로 넣은 곳
--
--    지금 화면은 (2) 로만 담는다 — 장소 표에서 담는 길(addExistingPlaceToList)이 코드에만
--    있고 화면에서 부르는 곳이 0 건이다. 그렇다고 (2) 만 두면 «우리가 아는 곳을 담는» 길이
--    영영 안 생기고, (1) 만 두면 **지금 되는 기능을 뺏는다** — 동네 가게나 여행자 개인
--    메모처럼 우리 목록에 없는 곳을 담을 수 없게 된다.
--
-- 🔴 종류를 값으로 갈라 둔다 — place_id 가 비었는지로 추론하게 만들지 않는다.
--
--    「place_id 가 null 이면 직접 적은 것」으로 두면, 나중에 «장소를 가리키는데 그 장소가
--    지워진» 행이 생겼을 때 그것이 직접 적은 것과 구분되지 않는다. 오늘 photo_subject 를
--    값으로 가른 것과 같은 이유다 — 뜻이 갈리면 값을 가른다.

CREATE TABLE collection (
    collection_id UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_collection_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,

    CONSTRAINT ck_collection_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- 화면이 부르는 것은 «내 컬렉션 전부» 다. 이름으로 찾는 길은 없다.
CREATE INDEX ix_collection_user ON collection (user_id, created_at DESC);

CREATE TABLE collection_item (
    collection_item_id UUID         PRIMARY KEY,
    collection_id      UUID         NOT NULL,
    -- PLACE = 장소 표를 가리킨다 · CUSTOM = 사용자가 직접 적었다
    kind               VARCHAR(20)  NOT NULL,

    -- kind = PLACE 일 때만 채운다
    place_id           UUID,

    -- kind = CUSTOM 일 때만 채운다
    name               VARCHAR(200),
    locality           VARCHAR(100),
    lat                DOUBLE PRECISION,
    lng                DOUBLE PRECISION,
    -- 🔴 사용자가 올린 사진의 주소. 기기 안 경로(photoUri)를 그대로 옮기면 다른 기기에서
    --    아무 의미가 없다 — 화면이 기존 이미지 업로드 경로로 먼저 올리고 그 주소를 준다.
    photo_url          VARCHAR(500),

    -- 두 종류가 함께 쓴다
    note               VARCHAR(500),
    -- 사용자가 정한 차례. 같은 값이 있어도 되고(끌어놓기 중간 상태), 화면이 다시 매긴다.
    position           INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_collection_item_collection
        FOREIGN KEY (collection_id) REFERENCES collection (collection_id) ON DELETE CASCADE,
    -- 🔴 장소가 지워지면 그 항목도 지운다. 가리킬 것이 없어진 «장소 항목» 은 화면에
    --    그릴 수가 없다 — 이름도 좌표도 그 장소에서 오기 때문이다. 직접 적은 항목은
    --    자기 안에 값이 있어서 영향을 안 받는다.
    CONSTRAINT fk_collection_item_place
        FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE CASCADE,

    CONSTRAINT ck_collection_item_kind CHECK (kind IN ('PLACE', 'CUSTOM')),

    -- 🔴 종류마다 채워야 하는 칸이 다르다. 이것을 DB 가 안 보면 «장소를 가리킨다면서
    --    place_id 가 빈» 행이 조용히 생기고, 화면은 그 항목을 못 그린다.
    CONSTRAINT ck_collection_item_place_shape
        CHECK (kind <> 'PLACE' OR (place_id IS NOT NULL AND name IS NULL)),
    CONSTRAINT ck_collection_item_custom_shape
        CHECK (kind <> 'CUSTOM' OR (place_id IS NULL AND name IS NOT NULL
               AND length(btrim(name)) > 0)),

    -- 같은 장소를 한 컬렉션에 두 번 담을 수 없다. 직접 적은 항목은 이름이 같아도 다른
    -- 곳일 수 있어 막지 않는다 (place_id 가 NULL 이면 UNIQUE 가 걸리지 않는다).
    CONSTRAINT uk_collection_item_place UNIQUE (collection_id, place_id)
);

CREATE INDEX ix_collection_item_collection
    ON collection_item (collection_id, position, created_at);

COMMENT ON TABLE collection IS
    '사용자가 만든 장소 묶음. 기기에만 있던 것을 서버로 옮긴 것이다(S15P21E201-1013).';
COMMENT ON COLUMN collection_item.kind IS
    'PLACE=장소 표를 가리킨다 · CUSTOM=사용자가 직접 적었다. place_id 가 비었는지로 추론하지 않는다.';
