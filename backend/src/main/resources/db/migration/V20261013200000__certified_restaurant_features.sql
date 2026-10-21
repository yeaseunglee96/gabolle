-- S15P21E201-2051 — 로컬 탐색 「맛집」 갈래: 표식 갈래 둘을 허용 목록에 더하고, 사전에 낱말을 넣는다.
--
-- 맛집은 공공기관이 인증한 식당만이다 — 부산시 맛집정보 · 구군 모범음식점 · 택슐랭(택시기사가 추천한 식당).
-- 이 식당들을 장소(place)에 붙는 표식(place_feature)으로 표시한다. 새로 더하는 종류는 둘이고 둘 다 태그형
-- (feature_key 가 반드시 있다)이다.
--
--   ADMIN_AREA_TAG     어느 구·동의 가게인가.        feature_key = '해운대구/우동'   value = {"gu":"해운대구","dong":"우동"}
--   CERTIFICATION_TAG  어느 목록이 인증했나.         feature_key = BUSAN_FOOD_INFO · MODEL_RESTAURANT · TAXI_DRIVER_PICK
--                                                    value = {"source":"모범음식점"} — 목록마다 한 줄
--
-- 맛집 갈래에 든다는 사실은 새 종류가 아니라 기존 탐색 갈래 서랍(INTEREST_TAG)에 낱말 LOCAL_RESTAURANT 를 더해 표시한다.
-- 그러면 /facets 가 건수를 세고, GET /places?facetType=INTEREST_TAG&facetKey=LOCAL_RESTAURANT 로도 나온다.
--
-- 🔴 구·동을 value(JSONB)가 아니라 feature_key 에 넣은 이유 — 목록을 구·동으로 거르고 세려면 DB 가 비교해야 하는데, 이 저장소의
--    조회는 JPQL(JPA 의 객체 질의어)이고 JPQL 은 JSONB 안을 못 본다(PlaceFeatureRepository.findByFeatureTypeIn 설명).
--    열쇠는 일반 칸이라 거르고 세고 ix_place_feature_type (feature_type, feature_key) 색인을 탄다. INTEREST_TAG·CATEGORY_TAG·
--    DESIRED_FOOD_TAG 가 낱말을 열쇠에 두는 것과 같은 이유다.
--
-- 🔴 이 두 목록은 **누적**이다. 바로 앞 정의의 목록을 그대로 복사한 뒤 새 값만 더했다.
--    ck_place_feature_type : 바로 앞 정의는 V20260930220000(RECOGNITION). 하나라도 빠뜨리면 그 갈래가 조용히 금지된다.
--    ck_place_feature_key_shape : 바로 앞 정의는 V20260916200000(SOUVENIR_ITEM_TAG). 태그형 목록 두 곳(IN / NOT IN)에 같이 더한다.
--    (PlaceFeatureTypeConstraintTest 가 앞의 것을 검사로 못 박는다.)
--
-- 🔴 적재기(CertifiedRestaurantLoader)와 같은 MR 에 넣는다 — 2026-09-22 에 적재기만 머지되고 이 목록이 빠져 운영에서
--    행이 전부 거절된 적이 있다(V20260922080000). 배포(마이그레이션)가 먼저, 적재가 나중이다.

-- ── 1. 갈래를 허용한다 ───────────────────────────────────────────────────────
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
            'SOUVENIR_ITEM_TAG',
            'MENU_PRICE_WON', 'WHY_VISIT',
            'BUSINESS_SUBCATEGORY',
            'MENU_ITEMS', 'FOREIGN_MENU', 'AMENITIES',
            'ADMISSION_FEE', 'NEARBY_LANDMARK', 'BEST_TIME',
            'RECOGNITION',
            -- 이번에 더한 것 — 공공 인증 식당(맛집)의 구·동과 인증한 목록
            'ADMIN_AREA_TAG', 'CERTIFICATION_TAG'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-2051 — 맛집 갈래의 구·동(ADMIN_AREA_TAG)과 인증한 목록(CERTIFICATION_TAG)을 더했다.';

-- ── 2. 둘 다 태그형이다 — feature_key 가 반드시 있어야 한다 ──────────────────
ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_key_shape;

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_key_shape
        CHECK (
            (feature_type IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                              'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                              'CATEGORY_TAG', 'DESIRED_FOOD_TAG', 'SOUVENIR_ITEM_TAG',
                              'ADMIN_AREA_TAG', 'CERTIFICATION_TAG')
             AND feature_key IS NOT NULL)
            OR
            (feature_type NOT IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                                  'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
                                  'CATEGORY_TAG', 'DESIRED_FOOD_TAG', 'SOUVENIR_ITEM_TAG',
                                  'ADMIN_AREA_TAG', 'CERTIFICATION_TAG')
             AND feature_key IS NULL));

-- ── 3. 사전에 낱말을 더한다 ──────────────────────────────────────────────────
--
-- place_feature_code 표 주석이 말하는 그대로 한다 — 「단어를 더할 때는 이 표에 행을 넣는다. 스키마를 고치지 않는다.」
-- 지금 외래키가 강제하는 것은 CATEGORY_TAG 하나뿐이라(dictionary_key 생성 칼럼) 아래 낱말은 사전에 **적어 두는** 것이다.
-- 자바 목록(ExtraInterestTagCode · CertifiedRestaurant.Source)과 같은지는 PlaceFeatureCodeMapTest 가 잰다.
--
-- 🔴 LOCAL_RESTAURANT 는 탐색 여덟 갈래(InterestTagCode)에 넣지 않았다. 여덟은 자료가 없어도 응답이 늘 전부 돌려주는
--    갈래이고, 맛집은 적재가 돌기 전에는 응답에 아예 없다가 돌고 나면 맨 뒤에 붙는다(ExtraInterestTagCode).
--    온보딩 취향 여섯(CATEGORY_TAG)과도 겹치지 않는다 — 겹치면 추천 점수가 조용히 달라진다.
INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES
    ('INTEREST_TAG', 'LOCAL_RESTAURANT', '맛집',
     '탐색 갈래이지만 여덟이 아니다 — 공공기관이 인증한 식당만. 적재가 돌기 전에는 응답에 없다 (S15P21E201-2051)'),
    ('CERTIFICATION_TAG', 'BUSAN_FOOD_INFO', '부산시 맛집정보',
     '부산광역시 맛집정보에 오른 식당 (S15P21E201-2051)'),
    ('CERTIFICATION_TAG', 'MODEL_RESTAURANT', '모범음식점',
     '구·군이 지정한 모범음식점. 장소 상세의 공인 표식(RECOGNITION)과 같은 낱말이다 (S15P21E201-2051)'),
    ('CERTIFICATION_TAG', 'TAXI_DRIVER_PICK', '택슐랭',
     '택시기사가 추천한 식당(택슐랭). 장소 상세의 공인 표식(RECOGNITION)과 같은 낱말이다 (S15P21E201-2051)')
ON CONFLICT (feature_type, feature_key) DO NOTHING;
