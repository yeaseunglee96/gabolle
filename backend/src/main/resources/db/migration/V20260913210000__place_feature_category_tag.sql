-- ══════════════════════════════════════════════════════════════════════════════
-- 장소 표식 한 갈래에 든 두 사전을 가른다 — S15P21E201-904
-- ══════════════════════════════════════════════════════════════════════════════
--
-- INTEREST_TAG 하나에 서로 겹치지 않는 두 어휘가 들어와 있었다.
--
--   온보딩 취향 여섯 : FOOD · CAFE_HEALING · SEA_BEACH · CITY · CULTURE_TEMPLE · NATURE_WALK
--   탐색 아코디언 여덟: FESTIVAL · NIGHT_MARKET · TRADITIONAL_MARKET · ACTIVITY ·
--                       WALK · NATURE · NIGHT_VIEW · SOUVENIR_SHOP
--
-- 둘은 같은 것을 다르게 부르는 것이 아니라 **다른 질문의 답**이다. 온보딩은 "어떤 여행을
-- 좋아하세요" 를 묻고 탐색은 "지금 뭘 구경할까" 를 묻는다. 서로 대응하는 말이 거의 없어서
-- 합칠 수가 없다 — 바다·도심·카페힐링·문화사찰·음식은 여덟에 짝이 없고, 축제·야시장·
-- 전통시장·야경·기념품샵은 여섯에 짝이 없다. 그래서 합치지 않고 가른다.
--
-- 🔴 왜 지금까지 사고가 안 났나 — 두 어휘에 **겹치는 단어가 우연히 하나도 없어서**다.
--    그런데 NATURE(탐색) 와 NATURE_WALK(온보딩) 처럼 닮은 말이 이미 한 갈래에 같이 있다.
--    누가 NATURE 를 온보딩 뜻으로 쓰는 순간 같은 단어가 두 뜻이 되고, **그때는 데이터만
--    봐서 구별할 수 없다.** 되돌릴 수 없게 되기 전에 가른다.
--
-- 🔴 이 자리는 V20260904020000 이 예고해 둔 곳이다. 그 파일의 주석:
--       "안쪽 코드값에는 CHECK 를 걸지 않았다 — 화면 옵션과 온톨로지가 확정된 뒤
--        같은 코드로 고정한다"
--    이 마이그레이션이 그 약속을 지키는 자리다. 다만 **CHECK 상수가 아니라 조회표**로
--    고정한다 — 설문(S15P21E201-714)이 술집·명소 같은 새 단어를 들고 올 것이 이미 보이는데,
--    CHECK 로 박으면 단어 하나 더할 때마다 마이그레이션이 된다. 조회표면 행 하나다.

-- ── 1. 새 갈래를 허용한다 ────────────────────────────────────────────────────
--
-- 🔴 이 목록은 **누적**이다. 바로 앞 마이그레이션(V20260911000000)의 목록을 그대로 복사한
--    뒤 새 값만 더한다. 하나라도 빠뜨리면 그 갈래가 조용히 금지된다.
--    (PlaceFeatureTypeConstraintTest 가 이 규칙을 검사로 못 박는다.)
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_type;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_type
        CHECK (feature_type IN (
            'INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
            'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
            'LOCALITY_SCORE', 'QUIETNESS_SCORE', 'TOURIST_RATIO',
            'POPULARITY_SCORE', 'CROWDING_SCORE', 'SHADE_SCORE', 'SLOPE_PERCENT',
            'STAIRS_PRESENT',
            'OPENING_HOURS', 'PRICE_LEVEL',
            'SOLO_FRIENDLY', 'BREAK_TIME', 'LAST_ORDER_TIME',
            'CHECK_IN_OUT',
            -- 이번에 더한 것
            'CATEGORY_TAG'));

-- CATEGORY_TAG 도 태그형이다 — feature_key 가 반드시 있어야 한다.
-- 🔴 이 목록도 누적이다. 태그형 여섯에 CATEGORY_TAG 를 더해 일곱이 된다.
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_key_shape;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_key_shape
        CHECK (
            (feature_type IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                              'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                              'CATEGORY_TAG')
             AND feature_key IS NOT NULL)
            OR
            (feature_type NOT IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                                  'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                                  'CATEGORY_TAG')
             AND feature_key IS NULL));

-- ── 2. 온보딩 여섯 갈래를 새 서랍으로 옮긴다 ────────────────────────────────
--
-- 값(feature_key)은 그대로 두고 갈래만 바꾼다. 유니크 인덱스는 (place_id, feature_type,
-- feature_key) 라 새 갈래로 옮기는 것은 충돌하지 않는다.
--
-- 2026-09-13 운영 기준으로 옮겨지는 행: FOOD 2355 · CULTURE_TEMPLE 162 · CITY 47 ·
-- CAFE_HEALING 26 · NATURE_WALK 23 · SEA_BEACH 2. 🔴 숫자는 그날의 실측이고 이 문장이
-- 판정 기준은 아니다 — 판정은 아래 검사와 완료 기준이 한다.
UPDATE place_feature
   SET feature_type = 'CATEGORY_TAG'
 WHERE feature_type = 'INTEREST_TAG'
   AND feature_key IN ('FOOD', 'CAFE_HEALING', 'SEA_BEACH', 'CITY',
                       'CULTURE_TEMPLE', 'NATURE_WALK');

-- ── 3. 서랍별 허용 단어를 조회표로 못 박는다 ───────────────────────────────
--
-- EAV(속성 이름까지 값으로 넣는 방식)의 대가가 여기 있다. 속성이 열이 아니라 문자열이라
-- 타입도 enum 도 외래키도 안 붙고, 그래서 **DB 가 어휘를 지키지 못한다.** 잃어버린 enum 을
-- 조회표 + 외래키로 되찾는다.
CREATE TABLE place_feature_code (
    feature_type VARCHAR(50) NOT NULL,
    feature_key  VARCHAR(50) NOT NULL,
    label_ko     TEXT,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_place_feature_code PRIMARY KEY (feature_type, feature_key)
);

COMMENT ON TABLE place_feature_code IS
    'S15P21E201-904 — 표식 갈래마다 올 수 있는 단어의 사전. place_feature 가 외래키로 이 표를 가리킨다. 🔴 단어를 더할 때는 이 표에 행을 넣는다 — 스키마를 고치지 않는다.';

-- 온보딩 취향 여섯 — 앱이 온보딩에서 묻고, 사용자의 CATEGORY 답과 겹침으로 비교한다.
INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES
    ('CATEGORY_TAG', 'SEA_BEACH',      '바다',      '온보딩 취향 여섯 (S15P21E201-904)'),
    ('CATEGORY_TAG', 'CITY',           '도심',      '온보딩 취향 여섯'),
    ('CATEGORY_TAG', 'CAFE_HEALING',   '카페·힐링', '온보딩 취향 여섯. 사 가는 곳이 아니라 머무는 곳'),
    ('CATEGORY_TAG', 'CULTURE_TEMPLE', '문화·사찰', '온보딩 취향 여섯'),
    ('CATEGORY_TAG', 'FOOD',           '음식',      '온보딩 취향 여섯'),
    ('CATEGORY_TAG', 'NATURE_WALK',    '자연·산책', '온보딩 취향 여섯');

-- 탐색 아코디언 여덟 — InterestTagCode 와 같아야 한다.
-- 🔴 NIGHT_MARKET · SOUVENIR_SHOP 은 아직 붙은 장소가 0곳이다. 그래도 사전에는 넣는다 —
--    화면이 여덟 줄을 항상 그리고, 원천 자료가 생기면 그때 채워진다.
INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES
    ('INTEREST_TAG', 'FESTIVAL',           '축제',     '탐색 여덟 갈래 (S15P21E201-473)'),
    ('INTEREST_TAG', 'NIGHT_MARKET',       '야시장',   '탐색 여덟 갈래. 원천에 신호가 없어 아직 0곳'),
    ('INTEREST_TAG', 'TRADITIONAL_MARKET', '전통시장', '탐색 여덟 갈래'),
    ('INTEREST_TAG', 'ACTIVITY',           '액티비티', '탐색 여덟 갈래'),
    ('INTEREST_TAG', 'WALK',               '산책',     '탐색 여덟 갈래'),
    ('INTEREST_TAG', 'NATURE',             '자연',     '탐색 여덟 갈래. 🔴 CATEGORY_TAG 의 NATURE_WALK 와 다른 말이다'),
    ('INTEREST_TAG', 'NIGHT_VIEW',         '야경',     '탐색 여덟 갈래'),
    ('INTEREST_TAG', 'SOUVENIR_SHOP',      '기념품샵', '탐색 여덟 갈래. 원천에 신호가 없어 아직 0곳');

-- 🔴 강제는 **사전을 가진 두 갈래에만** 건다.
--
-- 나머지 태그형(분위기·음식종류·알레르기·식단·접근성)은 어디에도 선언된 사전이 없다 —
-- 자바 enum 도 없고 CHECK 도 없다. 그 낱말 목록을 여기서 지어내면 그 순간 "저장소가 정한
-- 사전" 이 되어 버리고, 실제로 쓰이던 값이 배포 때 거부된다. **모르는 것을 아는 척하지
-- 않는다.** 그 갈래들의 사전은 각자 주인이 정할 때 이 표에 행을 더하면 된다.
--
-- 방법은 생성 칼럼이다. 강제 대상일 때만 값이 차고, 아니면 NULL 이라 복합 외래키가
-- 통과시킨다(MATCH SIMPLE — 칸 하나라도 NULL 이면 검사하지 않는다).
-- 🔴 점수형·참거짓형(feature_key 가 NULL)도 같은 이유로 자동으로 빠진다.
ALTER TABLE place_feature
    ADD COLUMN dictionary_key VARCHAR(50)
        GENERATED ALWAYS AS (
            CASE WHEN feature_type IN ('CATEGORY_TAG', 'INTEREST_TAG') THEN feature_key END
        ) STORED;

COMMENT ON COLUMN place_feature.dictionary_key IS
    'S15P21E201-904 — 사전 강제용 그림자 칸. 사전이 있는 갈래일 때만 feature_key 를 비추고 아니면 NULL 이라, 외래키가 그 갈래만 검사한다. 🔴 사람이 채우는 칸이 아니다.';

-- 🔴 이 제약을 더하는 순간 사전에 없는 낱말이 한 줄이라도 있으면 **배포가 여기서 멈춘다.**
--    일부러 그렇게 뒀다 — 조용히 넘어가면 두 사전이 섞인 채로 다음 사람에게 넘어간다.
--    멈추면 그 낱말을 사전에 넣을지(행 추가) 고칠지 사람이 정하면 된다.
ALTER TABLE place_feature
    ADD CONSTRAINT fk_place_feature_code
        FOREIGN KEY (feature_type, dictionary_key)
        REFERENCES place_feature_code (feature_type, feature_key);

-- ── 4. 대조표가 새 서랍을 가리키게 한다 ────────────────────────────────────
--
-- 🔴 추천 채점기는 갈래를 자바에 하드코딩하지 않고 **이 표를 읽어** 어느 서랍과 비교할지
--    정한다(BaselineCandidateTranslator). 그래서 이 한 줄이 채점기까지 따라간다.
UPDATE user_place_code_map
   SET place_feature_type = 'CATEGORY_TAG',
       note = '온보딩에서 고른 갈래가 겹치는가. 🔴 탐색 아코디언(INTEREST_TAG)과 다른 사전이다 (S15P21E201-904)'
 WHERE user_input_kind = 'PREFERENCE'
   AND user_input_code = 'CATEGORY'
   AND place_feature_type = 'INTEREST_TAG';

COMMENT ON COLUMN place_feature.feature_type IS
    '명세 6.2 의 피처 14종 + 영업시간·예상비용(S15P21E201-476) + 혼밥안심·브레이크타임·라스트오더(S15P21E201-265) + 숙박 체크인·체크아웃(S15P21E201-852) + 온보딩 갈래 CATEGORY_TAG(S15P21E201-904). 태그형은 feature_key 에 코드가 오고 나머지는 키가 없다. 🔴 안쪽 코드값은 place_feature_code 조회표가 외래키로 강제한다 — 더 이상 자유 문자열이 아니다.';
