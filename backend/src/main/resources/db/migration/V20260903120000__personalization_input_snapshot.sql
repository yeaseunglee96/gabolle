-- S15P21E201-554 — 개인화 입력(여행 조건·취향·제약)을 PostgreSQL 표로 만든다.
--
-- 왜 이 티켓인가. 같은 데이터를 서로 다르게 적은 것이 셋 있었다.
--   ERD v1.1(S15P21E201-36) : MySQL 기준. bigint auto_increment · json · varchar(191)
--   수집 명세(S15P21E201-542): PostgreSQL 기준. UUID · JSONB · TIMESTAMPTZ · 배열
--   요청 DTO(S15P21E201-461): preferenceSnapshotVersion 이 정수
-- 셋을 하나로 합치는 것이 이 티켓의 내용이다. 합의 기록은 2026-09-03 쪽지에 있다.
--
-- 🔴 이 파일은 표만 만든다. 엔티티(자바 클래스)는 만들지 않는다.
--    여행 저장 API 는 S15P21E201-461(모진성) 자리이고, 엔티티를 여기서 만들면
--    같은 표에 주인이 둘이 된다. hibernate.ddl-auto=validate 는 엔티티가 없는 표를
--    문제 삼지 않으므로, 표만 먼저 두고 그쪽이 얹는 것이 부딪히지 않는 순서다.
--
-- Flyway 버전을 순번(V1, V2)이 아니라 UTC 시각으로 적는다 — 여러 티켓이 동시에
-- 마이그레이션을 올릴 때 순번은 반드시 충돌하지만 시각은 안 겹친다 (S15P21E201-543 과 같은 규칙).

-- ── 여행 한 건 = 추천 입력의 뼈대 ──────────────────────────────────────────────
--
-- 수집 명세 4.2(여행 기본 조건) + 11.1(계획 여행 출발지).
CREATE TABLE trip (
    trip_id           UUID          PRIMARY KEY,
    owner_user_id     UUID          NOT NULL,

    -- 🔴 여행 조건의 판 번호. API 명세 TRIP-04(PATCH /trips/{tripId})의 응답이
    --    "여행 조건 버전 증가" 다. UUID 에는 순서가 없어서 "내가 본 판이 최신인가" 를
    --    판정할 수 없으므로, 겹치지 않는 키(UUID)와 순서를 세는 값(정수)을 둘 다 둔다.
    --    이미 머지된 ItineraryVersion 이 같은 모양이다 — id 는 UUID, version 은 정수.
    version           INTEGER       NOT NULL DEFAULT 1,

    start_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,

    -- 추천 계산용 출발 좌표. PostGIS 를 쓰게 되면 origin_point 로 바꾼다 (명세 11.1).
    origin_lat        DOUBLE PRECISION,
    origin_lng        DOUBLE PRECISION,
    origin_source     VARCHAR(20),
    -- 장기 분석용. 좌표는 지우더라도 "어느 지역에서 출발했는가" 는 남는다.
    origin_area_code  VARCHAR(30),

    budget_krw        BIGINT,
    party_size        INTEGER,
    time_window_start TIME,
    time_window_end   TIME,
    travel_modes      VARCHAR(30)[] NOT NULL DEFAULT '{}',

    timezone          VARCHAR(40)   NOT NULL DEFAULT 'Asia/Seoul',

    -- 값 목록은 S15P21E201-461(모진성)이 정했다 — 2026-09-03 쪽지.
    -- 처음에는 CHECK 를 비워 두었는데, 지어낸 값이 계약이 되는 것보다 담당자가 정하고
    -- 나서 박는 것이 맞기 때문이었다. 이제 정해졌으니 박는다.
    --
    -- 🔴 DELETED 는 deleted_at 과 뜻이 겹친다. "지워졌다" 를 말하는 자리가 둘이 되면
    --    둘이 어긋나는 날이 오고, 그때 어느 쪽이 맞는지 아무도 모른다. 그래서 열거값에
    --    DELETED 를 넣지 않는다 — 삭제는 deleted_at 하나로만 말한다.
    --    READY(일정 생성됨) · IN_PROGRESS(여행 중)는 deleted_at 으로 표현할 수 없으므로 넣는다.
    status            VARCHAR(20)   NOT NULL DEFAULT 'PLANNING',

    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    -- API 명세 2장 — 일반 자원은 soft delete(행을 지우지 않고 지운 시각만 적는다).
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT fk_trip_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_user (user_id),

    CONSTRAINT ck_trip_version   CHECK (version >= 1),
    CONSTRAINT ck_trip_dates     CHECK (end_date >= start_date),
    CONSTRAINT ck_trip_party     CHECK (party_size IS NULL OR party_size >= 1),
    CONSTRAINT ck_trip_budget    CHECK (budget_krw IS NULL OR budget_krw >= 0),

    CONSTRAINT ck_trip_status
        CHECK (status IN ('PLANNING', 'READY', 'IN_PROGRESS', 'COMPLETED')),

    CONSTRAINT ck_trip_origin_source
        CHECK (origin_source IS NULL
               OR origin_source IN ('ADDRESS', 'MAP', 'CURRENT_LOCATION')),

    -- 위도만 있고 경도가 없는 좌표는 좌표가 아니다. 반쪽만 저장되면 계산이
    -- 조용히 엉뚱한 곳을 가리킨다 — 오류가 아니라 틀린 답이 나온다.
    CONSTRAINT ck_trip_origin_pair
        CHECK ((origin_lat IS NULL) = (origin_lng IS NULL)),
    CONSTRAINT ck_trip_origin_range
        CHECK (origin_lat IS NULL
               OR (origin_lat BETWEEN -90 AND 90 AND origin_lng BETWEEN -180 AND 180)),

    -- 배열 안의 값까지 검사한다. 배열 칼럼은 CHECK 를 안 걸면 아무 문자열이나 들어간다.
    CONSTRAINT ck_trip_travel_modes
        CHECK (travel_modes <@ ARRAY['WALK', 'BUS', 'SUBWAY', 'TAXI', 'PRIVATE_CAR',
                                     'RENTAL_CAR', 'BICYCLE', 'FERRY', 'OTHER']::VARCHAR(30)[])
);

CREATE INDEX ix_trip_owner_created ON trip (owner_user_id, created_at DESC);

COMMENT ON COLUMN trip.version IS
    'API 명세 TRIP-04 의 여행 조건 판 번호. 스냅샷의 (trip_id, version) 과 짝이다.';
COMMENT ON COLUMN trip.status IS
    'PLANNING | READY(일정 생성됨) | IN_PROGRESS(여행 중) | COMPLETED. S15P21E201-461 이 정했다. 🔴 삭제 상태는 여기 없다 — deleted_at 하나로만 말한다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 취향 스냅샷 — 사용자가 직접 고른 것 (수집 명세 4.3)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 스냅샷은 불변이다. 그래서 updated_at 칸이 없다.
--    입력이 바뀌면 이 행을 고치는 것이 아니라 새 행을 만든다. 고칠 수 있게 두면
--    "그때 무슨 취향으로 추천했는가" 가 나중에 바뀌어 있고, 재현이 안 되는데도
--    데이터는 멀쩡해 보인다 (명세 4장 — "실제로 사용한 입력을 불변 스냅샷으로 고정한다").
CREATE TABLE preference_snapshot (
    preference_snapshot_id UUID        PRIMARY KEY,
    user_id                UUID        NOT NULL,

    -- 🔴 scope='USER'(계정 기본값)이면 NULL, scope='TRIP'(이번 여행 전용)이면 값이 있다.
    --    여행에서 고친 값이 계정 기본값을 덮어쓰면 안 된다 (명세 2.2).
    trip_id                UUID,

    version                INTEGER     NOT NULL,
    scope                  VARCHAR(10) NOT NULL,
    -- 어떤 설문 판으로 받은 답인가. ERD 의 UserTasteProfile.surveyVersion 자리다.
    survey_version         VARCHAR(100),
    created_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_preference_snapshot_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_preference_snapshot_trip
        FOREIGN KEY (trip_id) REFERENCES trip (trip_id),

    CONSTRAINT ck_preference_snapshot_version CHECK (version >= 1),
    CONSTRAINT ck_preference_snapshot_scope   CHECK (scope IN ('USER', 'TRIP')),
    CONSTRAINT ck_preference_snapshot_scope_trip
        CHECK ((scope = 'TRIP') = (trip_id IS NOT NULL))
);

-- 🔴 UNIQUE (trip_id, version) 을 제약으로 걸면 안 된다.
--    PostgreSQL 은 NULL 을 서로 다른 값으로 보므로 scope='USER' 행(trip_id IS NULL)이
--    전부 제약을 빠져나간다 — 같은 (user_id, version) 이 몇 개든 들어온다.
--    조건이 붙은 UNIQUE 색인(partial index) 둘로 나눠야 양쪽이 실제로 막힌다.
--    MySQL 에는 이 기능이 없어서 ERD 판에는 이 자리가 없었다.
CREATE UNIQUE INDEX uq_preference_snapshot_trip
    ON preference_snapshot (trip_id, version) WHERE trip_id IS NOT NULL;
CREATE UNIQUE INDEX uq_preference_snapshot_user
    ON preference_snapshot (user_id, version) WHERE trip_id IS NULL;

CREATE INDEX ix_preference_snapshot_user_created
    ON preference_snapshot (user_id, created_at DESC);

-- ── 취향 답 한 줄 ─────────────────────────────────────────────────────────────
CREATE TABLE preference_answer (
    preference_answer_id   UUID        PRIMARY KEY,
    preference_snapshot_id UUID        NOT NULL,
    dimension              VARCHAR(50) NOT NULL,
    value                  JSONB,
    answer_status          VARCHAR(10) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_preference_answer_snapshot
        FOREIGN KEY (preference_snapshot_id)
        REFERENCES preference_snapshot (preference_snapshot_id) ON DELETE CASCADE,

    CONSTRAINT uq_preference_answer_dimension
        UNIQUE (preference_snapshot_id, dimension),

    -- 명세 4.3 의 M1 확정 취향 차원 여덟. 값 안쪽 코드(어떤 카테고리·음식인지)는
    -- 화면 옵션과 장소 태그 온톨로지가 확정된 뒤 같은 코드로 고정한다 — 그래서
    -- 차원만 여기서 막고 value 안쪽은 막지 않는다.
    CONSTRAINT ck_preference_answer_dimension
        CHECK (dimension IN ('CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                             'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                             'SLOPE_PREFERENCE', 'SHADE_PREFERENCE')),

    -- 취향은 건너뛰기를 허용한다. 제약과 달리 NONE 이 없다 (명세 2.1).
    CONSTRAINT ck_preference_answer_status
        CHECK (answer_status IN ('SELECTED', 'SKIPPED', 'UNKNOWN')),

    -- 🔴 이 줄이 명세 2.1 을 DB 에서 강제하는 자리다.
    --    고른 답에는 값이 반드시 있고, 건너뜀·모름은 값을 실을 수 없다.
    --    이것을 안 막으면 SKIPPED 에 0 이나 빈 배열이 들어오고, 분석에서
    --    "안 좋아한다" 와 "안 물어봤다" 가 같은 값이 된다. 그때는 되돌릴 수 없다.
    CONSTRAINT ck_preference_answer_value_matches_status
        CHECK ((answer_status = 'SELECTED') = (value IS NOT NULL))
);

-- ══════════════════════════════════════════════════════════════════════════════
-- 제약 스냅샷 — 알레르기·식단·이동 (수집 명세 5장)
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE constraint_snapshot (
    constraint_snapshot_id UUID        PRIMARY KEY,
    user_id                UUID        NOT NULL,
    trip_id                UUID,
    version                INTEGER     NOT NULL,
    scope                  VARCHAR(10) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_constraint_snapshot_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_constraint_snapshot_trip
        FOREIGN KEY (trip_id) REFERENCES trip (trip_id),

    CONSTRAINT ck_constraint_snapshot_version CHECK (version >= 1),
    CONSTRAINT ck_constraint_snapshot_scope   CHECK (scope IN ('USER', 'TRIP')),
    CONSTRAINT ck_constraint_snapshot_scope_trip
        CHECK ((scope = 'TRIP') = (trip_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_constraint_snapshot_trip
    ON constraint_snapshot (trip_id, version) WHERE trip_id IS NOT NULL;
CREATE UNIQUE INDEX uq_constraint_snapshot_user
    ON constraint_snapshot (user_id, version) WHERE trip_id IS NULL;

CREATE INDEX ix_constraint_snapshot_user_created
    ON constraint_snapshot (user_id, created_at DESC);

-- ── 제약 한 줄 = 사실 하나 ────────────────────────────────────────────────────
--
-- 🔴 넓은 표 한 행(walk_minutes · wheelchair · stairs …)으로 두지 않는다.
--    그러면 응답 상태를 사실마다 가질 수 없어서 "휠체어는 답했고 계단은 안 물어봤다" 를
--    적을 칸이 사라진다. 그 구분이 명세 2.1 의 전부이므로 사실마다 한 행으로 둔다.
--    ERD 의 UserEffortBudget(체력 상한 한 행)이 이 표로 들어온 것이다.
CREATE TABLE constraint_answer (
    constraint_answer_id   UUID        PRIMARY KEY,
    constraint_snapshot_id UUID        NOT NULL,

    constraint_type        VARCHAR(30) NOT NULL,
    -- 종류 안에서 무엇에 대한 답인가. ALLERGY 면 알레르기 코드, DIET 면 식단 코드,
    -- MOBILITY 면 어떤 이동 조건인지.
    constraint_key         VARCHAR(40) NOT NULL,

    value                  JSONB,
    -- 점수로 상쇄되지 않고 후보에서 아예 빠지는 조건인가.
    hard                   BOOLEAN     NOT NULL,
    answer_status          VARCHAR(10) NOT NULL,

    -- ── 알레르기 전용 (명세 5.1) ──
    cross_contact_policy     VARCHAR(40),
    -- 🔴 자유 입력 알레르기 원문은 평문으로 두지 않는다. 일반 행동 로그에 복제하지도 않는다.
    other_allergy_ciphertext BYTEA,
    encryption_key_version   VARCHAR(30),
    encryption_nonce         BYTEA,

    -- ── 식단 전용 (명세 5.2) ──
    diet_requirement       VARCHAR(10),
    verification_policy    VARCHAR(30),

    created_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_constraint_answer_snapshot
        FOREIGN KEY (constraint_snapshot_id)
        REFERENCES constraint_snapshot (constraint_snapshot_id) ON DELETE CASCADE,

    CONSTRAINT uq_constraint_answer_key
        UNIQUE (constraint_snapshot_id, constraint_type, constraint_key),

    CONSTRAINT ck_constraint_answer_type
        CHECK (constraint_type IN ('ALLERGY', 'DIET', 'MOBILITY')),

    -- 🔴 제약은 건너뛰기를 허용하지 않는다. 안 물어본 것(UNKNOWN)과 없다고 답한 것(NONE)을
    --    구분해야 하고, "봤지만 건너뜀" 은 알레르기에서 허용할 수 없는 상태다 (명세 2.1).
    CONSTRAINT ck_constraint_answer_status
        CHECK (answer_status IN ('SELECTED', 'NONE', 'UNKNOWN')),

    CONSTRAINT ck_constraint_answer_value_matches_status
        CHECK ((answer_status = 'SELECTED') = (value IS NOT NULL)),

    -- 🔴 알레르기는 소프트 취향이 아니라 하드 제약이다 (명세 5.1, 2026-09-03 진미리 합의).
    --    hard=false 인 알레르기를 저장할 수 있게 두면 점수 계산에 섞여 들어가고,
    --    "덜 좋아함" 으로 취급된 땅콩이 결과에 남는다. 안전 문제라 DB 에서 막는다.
    CONSTRAINT ck_constraint_answer_allergy_is_hard
        CHECK (constraint_type <> 'ALLERGY' OR hard),

    -- 명세 5.3 의 이동 조건. 경사·그늘은 여기가 아니라 preference_answer 쪽이다 —
    -- 5.3 이 "이동약자 하드 제약과 경사·그늘 소프트 선호를 구분한다" 고 못 박았고,
    -- 두 곳에 두면 어느 쪽이 하드인지 표가 서로 다르게 말하게 된다.
    CONSTRAINT ck_constraint_answer_mobility_key
        CHECK (constraint_type <> 'MOBILITY'
               OR constraint_key IN ('MAX_WALKING_METERS', 'WHEELCHAIR', 'STROLLER',
                                     'HEAVY_LUGGAGE', 'STAIRS_AVOIDANCE')),

    -- 보행 상한은 숫자여야 한다. JSONB 는 "많이" 같은 문자열도 받으므로 종류를 검사한다.
    -- 🔴 캐스팅(::numeric)으로 검사하지 않는다 — 문자열이 들어오면 거짓이 아니라
    --    오류가 나고, CHECK 안에서 AND 의 평가 순서는 보장되지 않는다.
    CONSTRAINT ck_constraint_answer_walking_meters_is_number
        CHECK (constraint_key <> 'MAX_WALKING_METERS'
               OR answer_status <> 'SELECTED'
               OR jsonb_typeof(value -> 'meters') = 'number'),

    CONSTRAINT ck_constraint_answer_diet_key
        CHECK (constraint_type <> 'DIET'
               OR constraint_key IN ('HALAL', 'KOSHER', 'VEGETARIAN', 'VEGAN', 'PESCATARIAN',
                                     'NO_PORK', 'NO_BEEF', 'NO_SEAFOOD', 'GLUTEN_FREE',
                                     'LACTOSE_FREE', 'DAIRY_FREE', 'EGG_FREE', 'NUT_FREE',
                                     'NO_ALCOHOL', 'OTHER')),

    CONSTRAINT ck_constraint_answer_diet_requirement
        CHECK (diet_requirement IS NULL OR diet_requirement IN ('REQUIRED', 'PREFERRED')),
    CONSTRAINT ck_constraint_answer_verification_policy
        CHECK (verification_policy IS NULL
               OR verification_policy IN ('VERIFIED_ONLY', 'ALLOW_UNKNOWN_WITH_WARNING')),
    CONSTRAINT ck_constraint_answer_cross_contact
        CHECK (cross_contact_policy IS NULL
               OR cross_contact_policy IN ('AVOID_IF_CROSS_CONTACT_POSSIBLE',
                                           'ALLOW_WITH_WARNING', 'NOT_SPECIFIED')),

    -- 🔴 암호문만 있고 키 판 번호가 없으면 나중에 아무도 못 읽는다. 그건 저장이 아니라 유실이다.
    CONSTRAINT ck_constraint_answer_ciphertext_keyed
        CHECK (other_allergy_ciphertext IS NULL
               OR (encryption_key_version IS NOT NULL AND encryption_nonce IS NOT NULL)),
    CONSTRAINT ck_constraint_answer_ciphertext_only_allergy
        CHECK (other_allergy_ciphertext IS NULL OR constraint_type = 'ALLERGY')
);

COMMENT ON COLUMN constraint_answer.constraint_key IS
    'ALLERGY 는 알레르기 코드, DIET 는 식단 코드, MOBILITY 는 이동 조건 이름. 알레르기 코드 목록은 명세 5.1 이 "제품·온톨로지 팀 검토 후 확정" 으로 남겨 두었으므로 CHECK 를 걸지 않았다. 확정되면 CHECK 를 추가하는 마이그레이션을 올린다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- S15P21E201-543 이 이름을 맞춰 두고 남긴 자리 — 외래키를 이제 붙인다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 ON DELETE CASCADE 를 쓰지 않는다. 여행을 지웠다고 추천 기록이 함께 사라지면
--    "그때 무엇으로 계산했는가" 를 되짚을 수 없다 (NFR-08 재현성). 여행은 soft delete
--    이므로 행이 실제로 지워지는 일도 없다.
ALTER TABLE recommendation_job
    ADD CONSTRAINT fk_recommendation_job_trip
        FOREIGN KEY (trip_id) REFERENCES trip (trip_id),
    ADD CONSTRAINT fk_recommendation_job_preference_snapshot
        FOREIGN KEY (preference_snapshot_id)
        REFERENCES preference_snapshot (preference_snapshot_id),
    ADD CONSTRAINT fk_recommendation_job_constraint_snapshot
        FOREIGN KEY (constraint_snapshot_id)
        REFERENCES constraint_snapshot (constraint_snapshot_id);

COMMENT ON COLUMN recommendation_job.preference_snapshot_id IS
    '취향 스냅샷. S15P21E201-554 에서 외래키를 붙였다. REC-01 은 정수 preferenceSnapshotVersion 을 받고, 서버가 (trip_id, version) 으로 행을 찾아 이 UUID 를 적는다 — 클라이언트는 UUID 를 모른다.';
COMMENT ON COLUMN recommendation_job.constraint_snapshot_id IS
    '제약 스냅샷. S15P21E201-554 에서 외래키를 붙였다.';
