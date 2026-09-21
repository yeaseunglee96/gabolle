-- S15P21E201-1478 — 가격+narrative 적재기가 운영에서 한 줄도 못 넣던 것을 푼다.
--
-- 🔴 2026-09-22 07:21 운영에서 실제로 돌려 보고 나왔다. 적재기(S15P21E201-1465)는
--    `back/dev` 에 머지돼 있었는데, 돌리면 이렇게 끝났다.
--
--      ERROR: new row for relation "place_feature" violates check constraint
--             "ck_place_feature_type"
--      Detail: Failing row contains (..., WHY_VISIT, null, {"reasons": [...]}, ...)
--
--    한 줄도 안 들어가고 통째로 되돌려졌다(`source_version='price-narrative-20260922'` 로
--    세면 0행).
--
-- 🔴 적재기 커밋은 *"place_feature 가 feature_type 에 CHECK 를 안 걸어서 새 종류를
--    자유롭게 추가할 수 있다"* 고 적었다. **사실이 아니다.** 목록은 여기 있고, 마지막으로
--    고친 것이 V20260916200000(SOUVENIR_ITEM_TAG)이다. 그 뒤로 종류를 더한 것이 없어서
--    새 둘이 빠져 있었다.
--
--    자동 시험은 전부 통과했다(PlaceFeatureLoaderTest 13/13). **제약은 운영 스키마에
--    있고 시험은 거기까지 안 간다.** 돌려 보지 않으면 안 드러나는 종류다.
--
-- 🔴 `ck_place_feature_key_shape` 는 안 건드린다. 태그형(INTEREST_TAG 등)만 feature_key 를
--    요구하고 나머지는 NULL 이어야 하는데, 새 둘은 태그 목록 밖이라 NULL 쪽에 들어맞는다.
--    적재기도 실제로 NULL 로 넣는다(위 오류 줄의 feature_key 칸이 null 이다).
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
            -- 이번에 더한 것 — 가격+narrative 조사 산출물 (S15P21E201-1414 · -1465)
            --   MENU_PRICE_WON : 대표 메뉴 한 가지의 값. 못 찾은 곳은 0원으로 적지 않고
            --                    사실 자체를 안 낸다
            --   WHY_VISIT      : "왜 그 집에 가는지" 이유 목록 + 근거 주소
            'MENU_PRICE_WON', 'WHY_VISIT'));

COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-1478 — MENU_PRICE_WON(대표 메뉴 값)과 WHY_VISIT(왜 가는지 + 근거)을 더했다. 이 둘이 없어서 가격 적재가 운영에서 통째로 실패했다.';
