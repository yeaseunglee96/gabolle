-- S15P21E201-448 — "먹고 싶은 부산 음식"을 파는 식당을 잇는 표식을 더한다.
--
-- 🔴 CUISINE_TAG 를 재사용하지 않는다. CUISINE_TAG(MILMYEON·PORK_SOUP·SEAFOOD 등)는
--    BaselineCandidateScorer 가 취향 점수를 매길 때 이미 쓰고 있는 자리다
--    (AppFoodVocabulary 참고). 이 티켓이 원하는 것은 그것과 다른 질문이다 —
--    "밀면이 먹고 싶다" 를 고르면 그 밀면집이 일정에 들어가야 한다(F-PLN-05), 이건 취향
--    점수가 아니라 검색·필터링이다. 같은 서랍에 다른 질문의 답을 넣으면 채점기가 둘을
--    구별 못 하고, "새 표식은 기존 표식을 건드리지 않는다"(-448 완료 기준)를 어기게 된다.
--
-- 🔴 8종 전부를 넣지 않는다 — 지금 이름 매칭으로 검증 가능한 것은 BOKGUK(복국) 하나뿐이다.
--    운영에 이미 실린 2,355곳(S15P21E201-804) + TourAPI 음식점 656곳을 대조한 결과,
--    SSIAT_HOTTEOK·DONGNAE_PAJEON·BUSAN_EOMUK·NAKGOPSAE 는 이름 매칭으로 5곳(완료 기준)을
--    못 채운다(0~4곳) — 지어내지 않고 CHECK 목록에도 안 넣는다. 나머지는 상가정보 전체
--    CSV(53,716행, 서버 전용)를 확보한 뒤 값 자체와 함께 채운다.
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
            -- 이번에 더한 것
            'DESIRED_FOOD_TAG'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-448 — DESIRED_FOOD_TAG(먹고 싶은 부산 음식 8종 중 그 식당이 실제로 파는 것)를 더했다. CUISINE_TAG(취향 점수용)와는 다른 서랍이다.';

-- DESIRED_FOOD_TAG 도 태그형이다 — feature_key 가 반드시 있어야 한다(부산 음식 8종 코드,
-- frontend/src/plan/busanFoodCatalog.ts 의 code 와 같은 값).
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_key_shape;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_key_shape
        CHECK (
            (feature_type IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                              'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                              'CATEGORY_TAG', 'DESIRED_FOOD_TAG')
             AND feature_key IS NOT NULL)
            OR
            (feature_type NOT IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                                  'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                                  'CATEGORY_TAG', 'DESIRED_FOOD_TAG')
             AND feature_key IS NULL));
