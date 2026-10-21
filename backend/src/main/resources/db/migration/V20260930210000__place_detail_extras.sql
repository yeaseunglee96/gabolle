-- S15P21E201-1886 — 장소 상세에 보여 줄 새 표식 여섯 갈래를 허용 목록에 더한다.
--
-- 장소 상세 화면이 메뉴(한·영 이름, 값, 재료), 외국어 메뉴판이 있는지, 편의시설(와이파이·주차·화장실·예약·누리집),
-- 입장료, 가까운 이름난 곳, 가기 좋은 때(낮·밤·아무 때나 설문 수)를 보여 주려 한다. 값은 전부 place_feature 의
-- value(JSONB)에 싣는 「값형」이다 — feature_key 가 비어 있으므로 ck_place_feature_key_shape 와 place_feature_code
-- 외래키는 손대지 않는다.
--
--   MENU_ITEMS       {"items":[{"nameKo","nameEn","priceWon","ingredientsKo","ingredientsEn","signature"}]} 최대 30개
--   FOREIGN_MENU     {"available": bool}
--   AMENITIES        {"wifi","parking","restroom": bool|null, "reservation","homepage": 글|null}
--   ADMISSION_FEE    {"raw": 원문}
--   NEARBY_LANDMARK  {"name": 이름, "distanceM": 미터}
--   BEST_TIME        {"day","night","any": 설문 응답 수}
--
-- 🔴 알레르기는 여기 없다. ck_place_feature_safety_never_estimated 가 추정한 ALLERGEN/DIETARY/ACCESSIBILITY 행을
--    일부러 막는다 — 잘못 알려 주면 사람이 다친다. 재료는 MENU_ITEMS 안에 글로만 둔다. 그 제약은 건드리지 않는다.
--
-- 왜 적재기보다 먼저 이 파일인가: 2026-09-22 에 적재기가 머지됐는데 이 CHECK 목록에 갈래가 없어 운영에서 행이 전부
-- 거절됐다(가격+narrative, V20260922080000 이 뒤따라 고쳤다). 이번에는 적재기(PlaceDetailExtrasLoader)와 같은 MR 에
-- 목록을 같이 넣고, 시험이 여섯 갈래를 실제로 넣어 본다.
--
-- 버전은 back/dev 의 가장 큰 번호(V20260930190000) 다음이다. 갈래 목록은 바로 앞 정의(V20260930160000)를 그대로
-- 옮기고 여섯만 더했다(PlaceFeatureTypeConstraintTest 가 지킨다).

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
            -- 이번에 더한 것 — 장소 상세의 메뉴·편의시설·입장료·가까운 곳·가기 좋은 때
            'MENU_ITEMS', 'FOREIGN_MENU', 'AMENITIES',
            'ADMISSION_FEE', 'NEARBY_LANDMARK', 'BEST_TIME'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-1886 — 장소 상세용 MENU_ITEMS·FOREIGN_MENU·AMENITIES·ADMISSION_FEE·NEARBY_LANDMARK·BEST_TIME 을 더했다. 알레르기는 넣지 않는다.';
