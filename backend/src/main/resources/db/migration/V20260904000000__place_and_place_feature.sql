-- S15P21E201-262(place 부분) — 장소 정본·피처 표를 PostgreSQL 에 만든다.
--
-- 🔴 왜 지금 이 파일인가. 고지혁 님이 S15P21E201-545(장소 추천 피처 정본화, Highest,
--    M1 2026-09-04)를 시작하려 했는데 `place` 표 자체가 없어서 막혔다(2026-09-03
--    06:46 쪽지). Epic S15P21E201-42 밑 장소 관련 티켓 24개는 담당자가 붙었지만
--    표를 만드는 것은 이 파일이 처음이다.
--
-- 🔴 범위를 일부러 좁혔다 — 이 파일은 -545 를 풀 최소 스키마만 만든다.
--    영문명·카카오 평점·번역·영업시간처럼 Epic -42 의 다른 티켓(-88·-97·-300 등)이
--    다루는 칸은 여기서 만들지 않는다. 한 번에 다 만들면 그 티켓들의 담당자가
--    아직 안 정한 값을 여기서 대신 정하는 셈이 된다.
--
-- 🔴 S15P21E201-262(데이터 모델 26개 PostgreSQL·JPA 이관)와 겹치지 않는다 —
--    docs/DB-STANDARD.md 6절이 262 를 "종료 권고" 로 이미 정리했고(그 표 대부분은
--    -312·-543·-554·-313 이 각자 만들며 이미 끝났다), place 하나만 아무도 안 만들어
--    남아 있었다. 262 의 나머지 표는 이 파일이 손대지 않는다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 장소 정본 — 최소 식별 정보만
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 참고 코드(ref/local-route Prisma Place 모델)에는 60개가 넘는 칸이 있다. 그중
--    영업시간·주차·예약·카카오 평점처럼 아직 담당 티켓이 값을 안 정한 것은 옮기지
--    않는다 — 지어낸 기본값이 계약이 되는 것을 피한다(V120000 마이그레이션과 같은
--    원칙). 여기 없는 칸이 필요해지면 그 티켓이 새 마이그레이션으로 추가한다.
CREATE TABLE place (
    place_id    UUID             PRIMARY KEY,
    name_ko     VARCHAR(200)     NOT NULL,
    name_en     VARCHAR(200),
    -- 자유 문자열이다 — 값 목록이 아직 없다(S15P21E201-88 계열이 정할 자리).
    category    VARCHAR(50),
    address     VARCHAR(300),
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    created_at  TIMESTAMPTZ      NOT NULL,

    CONSTRAINT ck_place_origin_pair
        CHECK ((lat IS NULL) = (lng IS NULL)),
    CONSTRAINT ck_place_origin_range
        CHECK (lat IS NULL OR (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180))
);

CREATE INDEX ix_place_category ON place (category);

-- ══════════════════════════════════════════════════════════════════════════════
-- 장소 피처 — 사실 하나 = 한 행 (S15P21E201-545)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 넓은 표 한 행(카테고리·분위기·알레르기 …를 칼럼마다)으로 두지 않는다.
--    constraint_answer(V120000)와 같은 이유다 — 그러면 피처마다 출처·확인 상태를
--    따로 가질 수 없다. "카테고리는 검증됐고 알레르기는 추정" 을 적을 칸이 사라진다.
--
-- 🔴 feature_type·feature_key 에 CHECK 를 걸지 않는다. 사용자 Preference dimension
--    (CATEGORY·ATMOSPHERE·LOCALITY·QUIETNESS·TOURIST_PREFERENCE·FOOD_PREFERENCE ·
--    SLOPE_PREFERENCE·SHADE_PREFERENCE, V120000)과 장소 쪽 코드를 무엇으로 맞출지는
--    -545 의 "대조표" 작업이 아직 정하지 않았다. 여기서 값 목록을 미리 박으면 그
--    결정을 대신 내리는 셈이 된다 — 알레르기 코드(V120000)와 같은 이유로 비워 둔다.
CREATE TABLE place_feature (
    place_feature_id UUID        PRIMARY KEY,
    place_id         UUID        NOT NULL,

    feature_type     VARCHAR(50) NOT NULL,
    -- 종류 안에서 무엇에 대한 사실인가. 예: MOBILITY 라면 WHEELCHAIR·STAIRS 등.
    -- 값이 필요 없는 종류(예: 단일 값 피처)는 비워 둔다.
    feature_key      VARCHAR(50),

    value            JSONB,

    -- 🔴 -545 완료 기준이 그대로 요구하는 세 값이다. TripConstraint.EvidenceStatus
    --    (VERIFIED·PARTIAL·ESTIMATED·NEEDS_REVIEW·UNAVAILABLE, recommendation 패키지)
    --    와는 다른 값 목록이다 — 섞어 쓰지 않는다.
    evidence_status  VARCHAR(20) NOT NULL,

    -- 이 사실을 어디서 얻었는가. 자유 문자열 — MANUAL·TOURAPI·KAKAO 같은 값 목록은
    -- 아직 확정되지 않았다(Epic -42 의 개별 수집 티켓들이 정할 자리).
    source_type      VARCHAR(50),
    source_id        VARCHAR(200),
    observed_at      TIMESTAMPTZ,
    source_version   VARCHAR(100),

    created_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_place_feature_place
        FOREIGN KEY (place_id) REFERENCES place (place_id),

    CONSTRAINT ck_place_feature_evidence_status
        CHECK (evidence_status IN ('VERIFIED', 'ESTIMATED', 'UNKNOWN')),

    -- 🔴 결측값을 0 이나 안전한 값으로 바꿔치기하지 않는다(-545 완료 기준).
    --    UNKNOWN 이면 value 가 비어 있어야 한다 — "모른다" 를 값 있는 것처럼
    --    적으면 나중에 "몰랐다" 와 "그 값이었다" 를 구분할 수 없다.
    CONSTRAINT ck_place_feature_unknown_has_no_value
        CHECK (evidence_status <> 'UNKNOWN' OR value IS NULL)
);

CREATE INDEX ix_place_feature_place ON place_feature (place_id);
CREATE INDEX ix_place_feature_type ON place_feature (feature_type, feature_key);

COMMENT ON TABLE place_feature IS
    'S15P21E201-545 최소 스키마. 대조표(preference dimension ↔ place feature 코드)와 실제 값 적재는 이 표를 만든 뒤의 몫이다 — 이 마이그레이션은 표만 만든다.';
