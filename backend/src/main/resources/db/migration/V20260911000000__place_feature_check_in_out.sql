-- ══════════════════════════════════════════════════════════════════════════════
-- 숙박의 체크인·체크아웃을 담을 피처 갈래를 더한다 — S15P21E201-852
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 관광공사 자료에서 정규화한 영업시간 268곳 가운데 65곳이 숙박이고, 그 65곳은 영업시간 대신
-- 체크인·체크아웃 시각으로 온다(예: 체크인 15:00 · 체크아웃 11:00).
--
-- 🔴 OPENING_HOURS 에 섞지 않는다. 두 값이 답하는 질문이 다르다 — 영업시간은 "그 시각에 들어갈
--    수 있나" 이고 체크인·체크아웃은 "몇 시부터 방에 들어가고 몇 시에 나와야 하나" 다. 한 갈래로
--    합치면 일정 판정기가 숙소 행을 읽고 "오후 3시 전에는 문을 닫은 곳" 이라고 답하게 된다.
--    숙소는 그 시각에 닫은 것이 아니다.
--
-- feature_key 는 쓰지 않는다. ck_place_feature_key_shape 가 태그형 여섯만 키를 요구하고
-- 나머지는 키가 없어야 한다고 정해 뒀으므로 그 목록을 건드릴 일이 없다.
--
-- 기존 행은 전부 아래 목록 안의 값이라 NOT VALID 가 필요 없다 — V20260907160000 이
-- OPENING_HOURS·PRICE_LEVEL 을 더할 때와 같은 상황이다.

ALTER TABLE place_feature
    DROP CONSTRAINT ck_place_feature_type;

-- 🔴 이 목록은 **누적**이다. 앞선 마이그레이션이 더한 것을 하나라도 빠뜨리면 그 갈래가
--    조용히 금지된다 — 기존 행이 있으면 배포가 여기서 멈추고, 없으면 아무 일도 없다가
--    나중에 그 갈래를 넣는 적재가 실패한다. 그래서 **바로 앞 마이그레이션의 목록을 그대로
--    복사한 뒤 새 값만 더한다.** 지금 바로 앞은 V20260909020000 이고 SOLO_FRIENDLY ·
--    BREAK_TIME · LAST_ORDER_TIME 이 그 파일에서 들어왔다.
--    (이 규칙을 PlaceFeatureTypeConstraintTest 가 검사로 못 박는다.)
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
            -- 이번에 더한 것
            'CHECK_IN_OUT'));

COMMENT ON COLUMN place_feature.feature_type IS
    '명세 6.2 의 피처 14종 + 영업시간·예상비용(S15P21E201-476) + 혼밥안심·브레이크타임·라스트오더(S15P21E201-265) + 숙박 체크인·체크아웃(S15P21E201-852). 태그형은 feature_key 에 코드가 오고 나머지는 키가 없다. 🔴 안쪽 코드값에는 CHECK 를 걸지 않는다 — 화면 옵션과 온톨로지가 확정되면 같은 코드로 고정한다.';
