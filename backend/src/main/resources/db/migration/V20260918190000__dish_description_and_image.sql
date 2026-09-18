-- S15P21E201-1272 — 메뉴에서 읽은 음식 하나에 「어떤 음식인가」 설명과 그림을 붙인다.
--
-- 표가 셋인 이유는 셋이 서로 다른 것에 매여 있기 때문이다.
--
--   dish_description  음식 이름 + 언어   설명은 언어마다 다르다
--   dish_image        음식 이름          그림은 언어와 무관하다. 「돼지국밥」 그림은 하나면 된다
--   dish_image_usage  사람 + 시각        한도를 센다
--
-- 설명과 그림을 한 표에 합치면 언어 다섯 개마다 같은 그림을 다섯 번 만들게 된다.
-- 그림 한 장이 10초가 넘고 값도 제일 비싸므로 그 낭비가 가장 크다.

-- ── 설명 ────────────────────────────────────────────────────────────────────
--
-- 왜 저장해 두나: 부산에서 같은 메뉴를 찍는 사람은 여럿이다. 「돼지국밥」 설명을
-- 한 번 받아 두면 그 뒤로는 바깥 모델을 안 부른다 — 값도 시간(1.3~1.9초)도 0 이 된다.
CREATE TABLE dish_description (
    dish_description_id UUID        PRIMARY KEY,
    -- 🔴 사진에서 읽은 이름을 다듬은 것(앞뒤 공백 제거·소문자화). 다듬은 값으로 모으지
    --    않으면 「돼지국밥 」과 「돼지국밥」이 서로 다른 음식이 되어 두 번 만든다.
    name_key            TEXT        NOT NULL,
    -- 앱 언어(ko/en/ja/zh-Hans/zh-Hant). 설명은 이 언어로 적혀 있다.
    language            TEXT        NOT NULL,
    description         TEXT        NOT NULL,
    -- 그림을 만들 때 쓰는 영어 묘사. 설명과 같은 호출에서 함께 받아 둔다 —
    -- 그림을 만들 때 모델을 한 번 덜 부르려고 여기 적어 둔다.
    image_prompt        TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_dish_description UNIQUE (name_key, language)
);

COMMENT ON TABLE dish_description IS
    '음식 이름별·언어별 한 줄 설명. 사진에서 읽은 것이 아니라 모델이 아는 것이다 — 화면이 그렇게 그려야 한다 (S15P21E201-1272).';

-- ── 그림 ────────────────────────────────────────────────────────────────────
--
-- 🔴 이 표는 저장소이자 진행 상태다. 따로 「작업」 표를 두지 않았다.
--
--    그림은 이름 하나에 한 장이다. 그래서 「만드는 중」도 이름 하나에 하나이고,
--    그 둘을 다른 표에 두면 「행은 없는데 작업은 도는 중」 같은 사이 상태가 생긴다.
--    한 행의 status 로 두면 UNIQUE(name_key) 가 「같은 음식을 두 번 만들지 않는다」를
--    데이터베이스가 대신 지켜 준다 — 두 사람이 동시에 눌러도 한 번만 만든다.
CREATE TABLE dish_image (
    dish_image_id UUID        PRIMARY KEY,
    name_key      TEXT        NOT NULL,
    -- PENDING(만드는 중) · READY(있다) · FAILED(못 만들었다)
    status        TEXT        NOT NULL,
    -- 🔴 줄여서 JPEG 로 넣는다. 모델이 주는 것은 1MB 가 넘는 PNG 인데 화면에 손바닥만
    --    하게 뜨는 그림에 그 크기가 필요 없다. 자세한 근거는 DishImagePainter.
    image_bytes   BYTEA,
    content_type  TEXT,
    -- 못 만들었을 때 왜인지. 사용자에게 안 보인다 — 운영에서 판정하려고 남긴다.
    failure_note  TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_dish_image UNIQUE (name_key)
);

-- 「아직 안 끝난 것」을 쓸어 담을 때 쓴다 — 서버가 죽어 PENDING 인 채 남은 행을 찾는 질의다.
CREATE INDEX ix_dish_image_status_updated ON dish_image (status, updated_at);

COMMENT ON TABLE dish_image IS
    '음식 이름별로 만들어 둔 그림. 🔴 그 식당의 실제 음식 사진이 아니라 만들어진 그림이다 — 화면이 그렇게 말해야 한다 (S15P21E201-1272).';

-- ── 한도 ────────────────────────────────────────────────────────────────────
--
-- 🔴 메뉴판 읽기 한도(menu_scan_usage)를 같이 쓰지 않는다. 그림은 한 장에 10초가 넘고
--    값도 훨씬 비싸다. 한 표에 섞으면 그림 몇 장이 그날의 메뉴판 읽기까지 막는다 —
--    읽기는 알레르기 낱말을 보는 자리라 그쪽이 먼저 막히면 안 된다.
--
-- 세는 것은 menu_scan_usage 와 같은 모양이다. 만드는 것을 실제로 시작할 때만 센다 —
-- 이미 만들어 둔 그림을 꺼내 쓰는 것은 바깥을 안 부르므로 세지 않는다.
CREATE TABLE dish_image_usage (
    dish_image_usage_id UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_dish_image_usage_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE
);

CREATE INDEX ix_dish_image_usage_user_time ON dish_image_usage (user_id, requested_at DESC);
CREATE INDEX ix_dish_image_usage_time      ON dish_image_usage (requested_at);

COMMENT ON TABLE dish_image_usage IS
    '음식 그림 만들기 호출 기록. 메뉴판 읽기 한도와 따로 센다 — 그림이 읽기를 막으면 안 된다 (S15P21E201-1272).';
