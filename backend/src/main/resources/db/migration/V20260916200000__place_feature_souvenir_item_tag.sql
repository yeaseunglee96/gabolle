-- S15P21E201-471 — 기념품샵 취급 품목을 담을 표식을 더한다.
--
-- SOUVENIR_SHOP 은 이미 INTEREST_TAG 값으로 사전(place_feature_code)에 있다
-- (탐색 아코디언 여덟 갈래, V20260913210000). 그래서 새 마이그레이션 없이 쓸 수 있다.
--
-- 🔴 2026-09-16 정정 — 이 자리에 원래 "CATEGORY_TAG 값으로 있다 · 안쪽 코드값에는 CHECK 를
--    걸지 않는다" 고 적혀 있었다. 둘 다 틀렸다. (1) SOUVENIR_SHOP 은 INTEREST_TAG 쪽
--    낱말이다 — CATEGORY_TAG 사전에는 온보딩 취향 여섯(바다·도심·카페·문화·음식·자연)뿐이다.
--    (2) 막는 것은 CHECK 가 아니라 외래키 fk_place_feature_code 이고, CATEGORY_TAG 에는
--    실제로 걸려 있다. 그래서 CATEGORY_TAG:SOUVENIR_SHOP 을 넣으려던 적재기가 CI 에서
--    외래키 위반으로 죽었다. 낱말을 사전에 더해 통과시키는 길도 있었지만, 그러면 검사는
--    초록이 되고 기능은 여전히 안 된다 — /explore 화면이 INTEREST_TAG 만 보기 때문이다.
-- 이 마이그레이션이 더하는 것은 "그 가게가 무엇을 파는가" 를 담는 새 서랍
-- SOUVENIR_ITEM_TAG 하나뿐이다 — CATEGORY_TAG(갈래 판정)와 다른 질문이라 같은 서랍에
-- 안 넣는다(DESIRED_FOOD_TAG 를 CUISINE_TAG 와 분리한 것과 같은 이유, S15P21E201-448).
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
            'CATEGORY_TAG',
            'DESIRED_FOOD_TAG',
            -- 이번에 더한 것
            'SOUVENIR_ITEM_TAG'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-471 — SOUVENIR_ITEM_TAG(기념품샵 취급 품목)를 더했다. SOUVENIR_SHOP 은 이미 CATEGORY_TAG 값이라 새로 안 만든다.';

-- 태그형이다 — feature_key 가 반드시 있어야 한다.
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_key_shape;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_key_shape
        CHECK (
            (feature_type IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                              'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                              'CATEGORY_TAG', 'DESIRED_FOOD_TAG', 'SOUVENIR_ITEM_TAG')
             AND feature_key IS NOT NULL)
            OR
            (feature_type NOT IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                                  'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                                  'CATEGORY_TAG', 'DESIRED_FOOD_TAG', 'SOUVENIR_ITEM_TAG')
             AND feature_key IS NULL));
