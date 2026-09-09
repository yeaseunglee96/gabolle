-- S15P21E201-265 — 적재를 시작하기 전에 신규 장소 피처 세 종을 CHECK 목록에 더한다.
--
-- 🔴 왜 지금인가. 데이터 적재가 아직 시작되지 않았다(place_feature 를 채우는 코드가 없다,
--    V20260907003000 21행 참고). 지금 넣으면 마이그레이션 하나로 끝나지만, 적재가 시작된
--    뒤에 넣으면 이미 들어간 행을 전부 채워야 한다 — 지금이 가장 싸다.
--
-- 세 종 모두 place_feature(V20260904000000)에 사실 하나 = 한 행으로 들어간다. place 표에
-- 칼럼을 늘리지 않는 이유는 V20260904000000 자체의 원칙과 같다 — 출처·확인 상태를 값마다
-- 따로 가져야 한다("혼밥 안심"이라고 확정됐는지 추정인지, 브레이크타임을 언제 확인했는지).

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. SOLO_FRIENDLY — 혼밥·혼행 안심 테마 판정 근거 (참거짓형)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 태그형(TAG)이 아니라 참거짓형(FLAG)이다. "혼밥 안심"은 여러 값 중 하나를 고르는 분류가
--    아니라 그 장소가 혼자 온 손님에게 안심인지 아닌지를 묻는 이분법 사실이다 — 기존
--    참거짓형 STAIRS_PRESENT(V20260904020000)와 같은 모양이다. 그래서 feature_key 는 비운다
--    (ck_place_feature_key_shape 가 그대로 강제한다 — NOT IN 목록에 태그 6종만 있고
--    SOLO_FRIENDLY 는 거기 없으므로 자동으로 "키 없음"쪽에 들어간다).
--
-- 🔴 안전 필수 항목(ALLERGEN_TAG 등, ck_place_feature_safety_never_estimated)에 넣지
--    않는다. 혼밥 안심은 알레르기·접근성처럼 위반 시 후보를 제거하는 하드 필터가 아니라
--    선호로 가산되는 값이라서다 — user_place_code_map 대조는 그 배정이 정해지면 별도
--    마이그레이션이 잇는다(이 파일은 CHECK 통과만 다룬다, V20260908160000 1~2행과 같은 원칙).
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
            -- 여기서부터 이번(S15P21E201-265)에 더한 셋
            'SOLO_FRIENDLY', 'BREAK_TIME', 'LAST_ORDER_TIME'));

COMMENT ON COLUMN place_feature.feature_type IS
    '명세 6.2 의 피처 14종 + 영업시간·예상비용(-476) + 혼밥안심·브레이크타임·라스트오더(S15P21E201-265). 태그형은 feature_key 에 코드가 오고 나머지는 키가 없다. 🔴 안쪽 코드값에는 CHECK 를 걸지 않는다 — 화면 옵션과 온톨로지가 확정되면 같은 코드로 고정한다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. BREAK_TIME · LAST_ORDER_TIME — 최소 시각 사실 (참거짓형과 같은 자리, 값형)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 place 표에 영업시간 칸 자체를 아직 두지 않는다(V20260904000000 의 결정 — 영업시간
--    체계는 Epic -42 의 다른 티켓 몫). 이 티켓 범위는 "브레이크타임 시작/종료 시각·
--    라스트오더 시각만 최소로 추가"이므로, place_feature 가 이미 OPENING_HOURS(-476)에
--    쓰고 있는 것과 같은 자리(feature_key 없음, value 는 JSONB)를 그대로 재사용한다.
--    영업시간 전체 체계(요일별 구조 등)를 새로 설계하지 않는다 — 그건 이 티켓 몫이 아니다.
--
-- value 모양은 이 마이그레이션이 못 박지 않는다(OPENING_HOURS·PRICE_LEVEL 과 같은 원칙,
-- V20260907160000 91행) — 적재 담당이 실제 갱신 주기·표현을 정할 때 함께 정한다. 예상되는
-- 모양만 적어 둔다:
--   BREAK_TIME      value = {"start": "15:00", "end": "17:00"}
--   LAST_ORDER_TIME value = {"time": "21:30"}
--
-- 🔴 없으면(브레이크타임이 없는 가게) 행 자체를 만들지 않는다 — place_feature 의 기존
--    원칙과 같다. "모른다"와 "브레이크타임이 없다"를 구분해야 하면 evidence_status
--    (VERIFIED/UNKNOWN)로 표현한다.
COMMENT ON CONSTRAINT ck_place_feature_type ON place_feature IS
    'S15P21E201-265 — BREAK_TIME(브레이크타임 시작/종료)·LAST_ORDER_TIME(라스트오더 시각)을 더했다. 없으면 브레이크타임 중에 식사 일정이 잡히던 문제를 막는다. 값 모양은 OPENING_HOURS 와 같은 자리(feature_key 없음, JSONB)를 쓴다 — 새 표를 만들지 않는다.';
