-- S15P21E201-709 — 취향 차원 아홉째, SPEND_PROFILE(씀씀이 성향)을 더한다.
--
-- 🔴 이 값이 CHECK 를 통과하게 하는 것과, 장소 피처와 실제로 짝을 잇는 것은 다른 결정이다.
-- 이 파일은 앞의 것만 한다. place_feature 쪽에 씀씀이(가격대)를 나타낼 종류가 아직 없어서
-- user_place_code_map 에 이 차원의 실제 대조 행은 넣지 않는다 — 어떤 place_feature_type
-- (예: 새 PRICE_TIER_TAG)과 짝지을지는 place 담당의 결정이 필요하다. 그 전까지
-- PlaceFeatureCodeMapTest 의 UNPAIRED_BY_DESIGN 목록에 SPEND_PROFILE 을 넣어 둔다 —
-- "빠뜨린 것"과 "아직 짝이 없는 것"을 가르기 위해서다 (그 목록 자체의 존재 이유가 그것이다).
--
-- TasteDimension(자바 enum) 의 javadoc 이 적어 둔 대로, 이 목록은 세 곳에 함께 있고 셋이
-- 어긋나면 조용히 깨진다 — preference_answer · user_place_code_map · user_taste_weight.
-- 이번 마이그레이션이 그 세 CHECK 를 전부 고친다.

ALTER TABLE preference_answer
    DROP CONSTRAINT ck_preference_answer_dimension;
ALTER TABLE preference_answer
    ADD CONSTRAINT ck_preference_answer_dimension
        CHECK (dimension IN ('CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                             'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                             'SLOPE_PREFERENCE', 'SHADE_PREFERENCE', 'SPEND_PROFILE'));

ALTER TABLE user_place_code_map
    DROP CONSTRAINT ck_user_place_code_map_code;
ALTER TABLE user_place_code_map
    ADD CONSTRAINT ck_user_place_code_map_code
        CHECK (
            (user_input_kind = 'PREFERENCE' AND user_input_code IN (
                'CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                'SLOPE_PREFERENCE', 'SHADE_PREFERENCE', 'SPEND_PROFILE'))
            OR
            (user_input_kind = 'CONSTRAINT' AND user_input_code IN (
                'ALLERGY', 'DIET', 'MOBILITY'))
        );

ALTER TABLE user_taste_weight
    DROP CONSTRAINT ck_user_taste_weight_dimension;
ALTER TABLE user_taste_weight
    ADD CONSTRAINT ck_user_taste_weight_dimension
        CHECK (dimension IN ('CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                             'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                             'SLOPE_PREFERENCE', 'SHADE_PREFERENCE', 'SPEND_PROFILE'));
