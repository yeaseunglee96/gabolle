-- S15P21E201-545 — 장소 피처 코드를 확정하고, 사용자 입력 코드와의 대조표를 만든다.
--
-- 목적(티켓): 장소 속성이 비어 있는 것과 안전·접근성이 확인된 것을 구분하고, 사용자 취향과
-- 같은 코드로 매칭한다.
--
-- 🔴 place · place_feature 표 자체는 S15P21E201-262(모진성)가 만들었다. 여기서는 ALTER 만
--    한다. 재정의하지 않는다 — 표에 주인이 둘이 되면 안 된다.
--
-- 모진성 님이 feature_type · feature_key 값 목록을 일부러 비워 두셨다. 대조표가 이 티켓
-- 몫이라서다. 그 자리를 채운다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. 장소 본체에 출처와 데이터 판 (명세 6.1)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 지금은 place_feature 에만 출처가 있다. 그러면 "이 피처는 어디서 왔나" 는 답할 수 있지만
-- "이 장소 자체는 어느 수집분에서 왔나" 는 답할 수 없다. 티켓 완료 기준이
-- "추천에 사용된 모든 피처의 출처와 데이터 버전을 찾을 수 있다" 라서 둘 다 필요하다.
ALTER TABLE place
    ADD COLUMN source_type      VARCHAR(50),
    ADD COLUMN source_id        VARCHAR(200),
    ADD COLUMN collected_at     TIMESTAMPTZ,
    ADD COLUMN observed_at      TIMESTAMPTZ,
    ADD COLUMN dataset_version  VARCHAR(100);

COMMENT ON COLUMN place.dataset_version IS
    '이 장소 행이 어느 수집분에서 왔는가 (FR-REC-12). 🔴 추천 결과의 datasetVersion 과 맞춰 봐야 "그때 어느 데이터로 계산했나" 를 되짚을 수 있다.';
COMMENT ON COLUMN place.collected_at IS
    '우리가 가져온 시각. observed_at(원천에서 관측된 시각)과 다르다 — 어제 수집한 지난달 영업시간이 있을 수 있다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. 🔴 피처마다 현재 값은 하나다 — 이력은 여기 쌓지 않는다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 지금 place_feature 에는 유일성 제약이 하나도 없다. 그래서 같은
-- (place_id, feature_type, feature_key) 가 서로 다른 값으로 두 번 들어갈 수 있고,
-- 어느 것이 현재 값인지 아무것도 말하지 않는다. 읽는 쪽마다 "observed_at 최신" 을
-- 각자 고르면 언젠가 갈린다.
--
-- **이력을 여기 쌓지 않는 이유.** 추천에 실제로 쓴 값은 이미 다른 곳에 있다 —
-- recommendation_candidate.feature_values 가 요청 시점의 스냅샷을 그대로 담는다
-- (S15P21E201-543). 티켓 완료 기준이 말하는 "추천에 사용된 피처" 는 그 스냅샷이다.
-- 여기에 이력을 또 두면 "그때 그늘 점수가 얼마였나" 에 답이 둘이 되고, 어느 쪽이
-- 정본인지 아무것도 말해 주지 않는다.
--
-- 🔴 feature_key 가 nullable 이라 그냥 UNIQUE 를 걸면 안 된다. PostgreSQL 은 NULL 을
--    서로 다르게 보므로 키 없는 행(점수형 피처)이 전부 제약을 빠져나간다.
--    preference_snapshot 에서 같은 함정을 밟았고 같은 방법으로 막는다 — 부분 색인 둘.
CREATE UNIQUE INDEX uq_place_feature_keyed
    ON place_feature (place_id, feature_type, feature_key) WHERE feature_key IS NOT NULL;
CREATE UNIQUE INDEX uq_place_feature_unkeyed
    ON place_feature (place_id, feature_type) WHERE feature_key IS NULL;

-- ══════════════════════════════════════════════════════════════════════════════
-- 3. 피처 종류를 확정한다 (명세 6.2)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 태그형(TAG)은 feature_key 에 코드가 오고, 점수형(SCORE)·참거짓형(FLAG)은 키가 없다.
-- 그 구분을 CHECK 로 못 박는다 — 안 그러면 같은 피처가 어떤 행은 키를 갖고 어떤 행은
-- 안 갖는 상태가 되고, 위의 유일성이 무의미해진다.
ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_type
        CHECK (feature_type IN (
            -- 태그형 — feature_key 에 코드가 온다
            'INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
            'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG',
            -- 점수형 — feature_key 없음
            'LOCALITY_SCORE', 'QUIETNESS_SCORE', 'TOURIST_RATIO',
            'POPULARITY_SCORE', 'CROWDING_SCORE', 'SHADE_SCORE', 'SLOPE_PERCENT',
            -- 참거짓형 — feature_key 없음
            'STAIRS_PRESENT')),

    -- 🔴 태그형은 키가 반드시 있고, 그 밖은 반드시 없다.
    ADD CONSTRAINT ck_place_feature_key_shape
        CHECK (
            (feature_type IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                              'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG')
             AND feature_key IS NOT NULL)
            OR
            (feature_type NOT IN ('INTEREST_TAG', 'ATMOSPHERE_TAG', 'CUISINE_TAG',
                                  'ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG')
             AND feature_key IS NULL)
        );

COMMENT ON COLUMN place_feature.feature_type IS
    '명세 6.2 의 피처 14종. 태그형은 feature_key 에 코드가 오고 점수형·참거짓형은 키가 없다. 🔴 안쪽 코드값(어떤 관심 태그인지 등)에는 CHECK 를 걸지 않았다 — 화면 옵션과 온톨로지가 확정된 뒤 같은 코드로 고정한다 (명세 4.3).';

-- ══════════════════════════════════════════════════════════════════════════════
-- 4. 대조표 — 사용자 입력 코드와 장소 피처를 잇는다 (티켓 작업 내용 5)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 명세 4.3 — "사용자 입력 코드와 장소 피처 코드가 달라지면 안 된다."
-- 그 말은 어딘가에 대조가 적혀 있어야 한다는 뜻이다. 코드에만 두면 분석 쿼리가 볼 수 없고,
-- 문서에만 두면 코드와 갈린다. 표로 둬서 둘 다 같은 것을 본다.
--
-- 🔴 취향만이 아니라 제약도 사용자 입력이다. 알레르기·식단·이동은 장소 쪽에 짝이 있어야
--    "확인 안 된 곳을 안전하다고 표시하지 않는다" 를 계산할 수 있다 (명세 6.2 마지막 줄).
CREATE TABLE user_place_code_map (
    user_input_kind    VARCHAR(20) NOT NULL,
    user_input_code    VARCHAR(50) NOT NULL,
    place_feature_type VARCHAR(50) NOT NULL,

    -- 어떻게 맞추는가. 판정 방법이 다르면 같은 표에 있어도 다른 계산이다.
    match_kind         VARCHAR(20) NOT NULL,
    note               TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_place_code_map
        PRIMARY KEY (user_input_kind, user_input_code, place_feature_type),

    CONSTRAINT ck_user_place_code_map_kind
        CHECK (user_input_kind IN ('PREFERENCE', 'CONSTRAINT')),

    -- 🔴 왼쪽 코드는 실제로 쓰이는 것이어야 한다. preference_answer.dimension 과
    --    constraint_answer.constraint_type 의 CHECK 목록과 같아야 한다 — 여기서 어긋나면
    --    대조표가 없는 코드를 가리키고, 그건 대조표가 아니라 오해의 근원이 된다.
    CONSTRAINT ck_user_place_code_map_code
        CHECK (
            (user_input_kind = 'PREFERENCE' AND user_input_code IN (
                'CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                'SLOPE_PREFERENCE', 'SHADE_PREFERENCE'))
            OR
            (user_input_kind = 'CONSTRAINT' AND user_input_code IN (
                'ALLERGY', 'DIET', 'MOBILITY'))
        ),

    CONSTRAINT ck_user_place_code_map_match_kind
        CHECK (match_kind IN ('TAG_OVERLAP', 'SCORE_COMPARE', 'FLAG_COMPARE', 'HARD_FILTER'))
);

COMMENT ON TABLE user_place_code_map IS
    'S15P21E201-545 — 사용자 입력 코드와 장소 피처를 잇는 대조표. 명세 4.3 "사용자 입력 코드와 장소 피처 코드가 달라지면 안 된다" 를 표로 못 박은 것이다. 🔴 빠짐이 없는지는 자동 검사가 본다 (PlaceFeatureCodeMapTest).';
COMMENT ON COLUMN user_place_code_map.match_kind IS
    'TAG_OVERLAP(태그가 겹치나) · SCORE_COMPARE(점수를 선호와 비교) · FLAG_COMPARE(참거짓 비교) · HARD_FILTER(🔴 위반이면 후보에서 제거. 알레르기·필수 식단·검증된 접근 불가)';

-- ── 대조 내용 — 명세 4.3(취향 8차원) · 5장(제약 3종) · 6.2(피처 14종) ────────
--
-- 🔴 HARD_FILTER 인 셋이 안전에 걸리는 자리다. 점수로 상쇄되지 않는다 (FR-REC-02).
INSERT INTO user_place_code_map
    (user_input_kind, user_input_code, place_feature_type, match_kind, note) VALUES
    -- 취향 여덟
    ('PREFERENCE', 'CATEGORY',           'INTEREST_TAG',        'TAG_OVERLAP',   '관심 태그가 겹치는가'),
    ('PREFERENCE', 'ATMOSPHERE',         'ATMOSPHERE_TAG',      'TAG_OVERLAP',   '분위기 태그가 겹치는가'),
    ('PREFERENCE', 'FOOD_PREFERENCE',    'CUISINE_TAG',         'TAG_OVERLAP',   '음식 태그가 겹치는가'),
    ('PREFERENCE', 'LOCALITY',           'LOCALITY_SCORE',      'SCORE_COMPARE', '로컬성 점수'),
    ('PREFERENCE', 'QUIETNESS',          'QUIETNESS_SCORE',     'SCORE_COMPARE', '조용함 점수'),
    ('PREFERENCE', 'TOURIST_PREFERENCE', 'TOURIST_RATIO',       'SCORE_COMPARE', '관광객 비율'),
    ('PREFERENCE', 'SHADE_PREFERENCE',   'SHADE_SCORE',         'SCORE_COMPARE', '그늘 점수'),
    ('PREFERENCE', 'SLOPE_PREFERENCE',   'SLOPE_PERCENT',       'SCORE_COMPARE', '경사도'),
    -- 제약 셋 — 전부 하드 필터다
    ('CONSTRAINT',  'ALLERGY',           'ALLERGEN_TAG',        'HARD_FILTER',
        '🔴 위반이면 제거. 정보가 없으면 PASS 로 바꾸지 않고 UNKNOWN 으로 둔다 (명세 5.3)'),
    ('CONSTRAINT',  'DIET',              'DIETARY_SUPPORT_TAG', 'HARD_FILTER',
        '🔴 diet_requirement=REQUIRED 는 하드. PREFERRED 는 경고로 내려간다'),
    ('CONSTRAINT',  'MOBILITY',          'ACCESSIBILITY_TAG',   'HARD_FILTER',
        '🔴 검증된 접근 불가만 하드. 정보 없음은 UNKNOWN 이고 안전하다는 뜻이 아니다'),
    ('CONSTRAINT',  'MOBILITY',          'STAIRS_PRESENT',      'FLAG_COMPARE',
        '계단 회피 요구와 대조. 정보 없음은 UNKNOWN');

-- 🔴 짝이 없는 피처 둘 — POPULARITY_SCORE · CROWDING_SCORE 는 대조표에 없다.
--    사용자가 "인기 있는 곳" 이나 "붐비지 않는 곳" 을 직접 고르는 화면이 없기 때문이다
--    (명세 4.3 M1 확정 차원 여덟에 없다). 랭킹 가중치로만 쓰인다.
--    빠뜨린 것이 아니라 짝이 없는 것이고, 자동 검사도 그렇게 알고 있다.
