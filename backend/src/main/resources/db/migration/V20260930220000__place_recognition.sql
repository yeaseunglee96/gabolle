-- S15P21E201-1891 — 장소 상세에 보여 줄 「공인 표식」 갈래 RECOGNITION 을 허용 목록에 더한다.
--
-- 표식은 두 가지다. 둘 다 공공데이터포털의 부산광역시 자료이고 이용허락범위(자료를 어디까지 써도 되는지 정한
-- 조건)가 「제한 없음」이다.
--   MODEL_RESTAURANT  구·군이 지정한 모범음식점 — 「부산광역시_구군 모범음식점 현황」
--   TAXI_DRIVER_PICK  택시기사가 추천한 식당 — 「부산광역시 택슐랭 선정 식당(2025)」
--
-- 값형(feature_key 가 비어 있고 value(JSONB) 에 싣는 것)이다. 한 장소에 한 줄, 그 안에 표식을 전부 담는다.
--   RECOGNITION  {"badges":[{"kind":"MODEL_RESTAURANT"|"TAXI_DRIVER_PICK","since":"YYYY-MM-DD"|null,
--                            "menu":글|null,"source":출처 글}]}  1~5개
--
-- 버전은 back/dev 의 가장 큰 번호(V20260930210000) 다음이다. 갈래 목록은 바로 앞 정의(V20260930210000)를 그대로
-- 옮기고 RECOGNITION 하나만 더했다(PlaceFeatureTypeConstraintTest 가 지킨다).

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
            -- 이번에 더한 것 — 모범음식점·택시기사 추천 표식
            'RECOGNITION'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-1891 — 공인 표식 RECOGNITION(모범음식점·택시기사 추천)을 더했다.';
