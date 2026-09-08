-- S15P21E201-543 — 추천 요청·전체 후보·제약 판정·최종 순위를 request_id 로 저장한다.
--
-- 분석 단위는 (request_id, place_id) 다. Top-K 만 남기면 "왜 이것이 안 나왔는가" 를
-- 영영 되물을 수 없으므로, 한 요청에서 생긴 후보를 탈락한 것까지 전부 남긴다.
--
-- Flyway(**앱이 뜰 때 이 SQL 을 순서대로 한 번씩 실행해 DB 스키마를 맞추는 도구**)
-- 버전을 숫자 순번(V1, V2) 이 아니라 UTC 시각(V20260902090000)으로 적는다.
-- 여러 티켓이 동시에 마이그레이션을 올릴 때 순번은 반드시 충돌하지만 시각은 안 겹친다.
--
-- 🔴 S15P21E201-542 의 preference/constraint 스냅샷 테이블은 아직 없다.
--    그래서 preference_snapshot_id · constraint_snapshot_id 는 UUID 컬럼으로만 두고
--    FK(**다른 테이블의 행을 가리키는 제약. 가리키는 행이 없으면 저장을 거부한다**)는
--    걸지 않는다. 542 가 병합된 뒤 ALTER TABLE 로 FK 만 더하면 되도록 이름을 맞춰 뒀다.

-- ── 추천 요청 한 건 = Job 한 행 ────────────────────────────────────────────────
CREATE TABLE recommendation_job (
    job_id                          UUID        PRIMARY KEY,
    request_id                      UUID        NOT NULL UNIQUE,
    user_id                         UUID        NOT NULL,
    trip_id                         UUID,
    trip_version                    INTEGER,
    preference_snapshot_id          UUID,
    constraint_snapshot_id          UUID,
    itinerary_id                    UUID,
    itinerary_version               INTEGER,

    -- GB-API-001 4.2 JobDto 가 요구하는 칸들. 공개 응답이 이 값들을 그대로 싣는다.
    job_type                        VARCHAR(40) NOT NULL,
    resource_type                   VARCHAR(20),
    resource_id                     UUID,
    base_version                    INTEGER,
    retryable                       BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 🔴 아래 둘은 비동기 Job 러너(REC-01 · JOB-01)의 값이다. 그 러너는 S15P21E201-543
    --    범위가 아니라서 여기서는 채우지 않는다. 임의의 기본값을 넣으면 클라이언트가
    --    지키지도 않을 폴링 주기를 믿게 된다.
    poll_after_seconds              INTEGER,
    expires_at                      TIMESTAMPTZ,

    job_status                      VARCHAR(20) NOT NULL,
    job_stage                       VARCHAR(30),
    progress_percent                INTEGER     NOT NULL DEFAULT 0,
    generated_candidate_count       INTEGER     NOT NULL DEFAULT 0,
    eligible_candidate_count        INTEGER     NOT NULL DEFAULT 0,
    returned_candidate_count        INTEGER     NOT NULL DEFAULT 0,

    model_version                   VARCHAR(100),
    feature_version                 VARCHAR(100),
    ontology_version                VARCHAR(100),
    -- 🔴 policy_version 은 온톨로지 판정에 쓰인 정책 버전이다. ontology_version 과 다르다 —
    --    어휘·규칙이 그대로여도 정책(무엇을 REQUIRED 로 볼 것인가)이 바뀌면 판정이 바뀐다.
    --    GB-API-001 4.3 이 추천 응답의 필수 항목으로 요구한다.
    policy_version                  VARCHAR(100),
    dataset_version                 VARCHAR(100),
    service_version                 VARCHAR(100),
    app_version                     VARCHAR(50),
    deployment_environment          VARCHAR(30),

    created_at                      TIMESTAMPTZ NOT NULL,
    generated_at                    TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,
    total_latency_ms                BIGINT,
    candidate_generation_latency_ms BIGINT,
    ontology_latency_ms             BIGINT,
    feature_lookup_latency_ms       BIGINT,
    ranking_latency_ms              BIGINT,
    optimization_latency_ms         BIGINT,

    error_code                      VARCHAR(64),
    failure_stage                   VARCHAR(30),
    retry_count                     INTEGER     NOT NULL DEFAULT 0,
    timeout_occurred                BOOLEAN     NOT NULL DEFAULT FALSE,
    fallback_reason                 VARCHAR(64),
    fallback_mode                   VARCHAR(20),

    -- 🔴 GB-API-001 5장 JobStatus enum 그대로다. 여기에 'FALLBACK' 은 없다 —
    --    대체 경로로 만들었다는 사실은 상태가 아니라 fallback_mode 가 나타낸다.
    --    둘을 섞으면 "성공했는데 기준선이었다" 를 표현할 칸이 사라진다.
    CONSTRAINT ck_recommendation_job_status
        CHECK (job_status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'EXPIRED')),

    CONSTRAINT ck_recommendation_job_type
        CHECK (job_type IN ('ITINERARY_GENERATION', 'ITEM_REPLACE', 'ITEM_REMOVE',
                            'ITEM_ORDER_CHANGE', 'ITINERARY_RECALCULATE', 'NOW_RECOMMENDATION')),

    CONSTRAINT ck_recommendation_job_resource
        CHECK ((resource_type IS NULL AND resource_id IS NULL)
               OR (resource_type IN ('TRIP', 'ITINERARY') AND resource_id IS NOT NULL)),

    CONSTRAINT ck_recommendation_job_fallback_mode
        CHECK (fallback_mode IS NULL OR fallback_mode IN ('MODEL', 'RULE', 'BASELINE')),

    CONSTRAINT ck_recommendation_job_progress
        CHECK (progress_percent BETWEEN 0 AND 100),

    -- 후보 수는 Candidate 행마다 반복하지 않고 여기 단계별로 한 번만 적는다.
    -- 반환 ⊆ 랭킹 가능 ⊆ 생성 이 깨지면 그 자체가 파이프라인 버그다.
    CONSTRAINT ck_recommendation_job_counts
        CHECK (generated_candidate_count >= 0
               AND eligible_candidate_count BETWEEN 0 AND generated_candidate_count
               AND returned_candidate_count BETWEEN 0 AND eligible_candidate_count),

    -- 🔴 버전을 못 구했다고 'unknown' · 'v1' 같은 값을 넣지 않는다.
    --    못 구하면 그 요청은 FAILED 로만 남을 수 있다. 성공한 Job 에 버전이 비어 있으면
    --    나중에 "그때 무엇으로 계산했는가" 를 재현할 수 없으므로 DB 가 먼저 거부한다.
    CONSTRAINT ck_recommendation_job_versions_present
        CHECK (job_status <> 'SUCCEEDED'
               OR (model_version IS NOT NULL
                   AND feature_version IS NOT NULL
                   AND ontology_version IS NOT NULL
                   AND policy_version IS NOT NULL
                   AND dataset_version IS NOT NULL
                   AND service_version IS NOT NULL
                   AND deployment_environment IS NOT NULL)),

    -- 🔴 성공한 추천은 "어떤 취향·제약으로 만들었는가" 를 반드시 되짚을 수 있어야 한다.
    --    스냅샷 ID 가 비면 그 요청은 나중에 재현도 설명도 안 되는데, 데이터는 멀쩡해 보인다.
    --    버전과 같은 이유로 같은 모양으로 조인다.
    CONSTRAINT ck_recommendation_job_snapshots_present
        CHECK (job_status <> 'SUCCEEDED'
               OR (preference_snapshot_id IS NOT NULL AND constraint_snapshot_id IS NOT NULL)),

    -- 실패는 원인 없이 남지 않는다. 후보가 0건이어도 이 줄 때문에 이유가 반드시 남는다.
    CONSTRAINT ck_recommendation_job_failure_has_reason
        CHECK (job_status <> 'FAILED' OR error_code IS NOT NULL)
);

CREATE INDEX ix_recommendation_job_user_created
    ON recommendation_job (user_id, created_at DESC);
CREATE INDEX ix_recommendation_job_trip_created
    ON recommendation_job (trip_id, created_at DESC);
CREATE INDEX ix_recommendation_job_status_created
    ON recommendation_job (job_status, created_at DESC);

COMMENT ON COLUMN recommendation_job.preference_snapshot_id IS
    'S15P21E201-542 의 취향 스냅샷 ID. 542 병합 뒤 FK 를 추가한다.';
COMMENT ON COLUMN recommendation_job.constraint_snapshot_id IS
    'S15P21E201-542 의 제약 스냅샷 ID. 542 병합 뒤 FK 를 추가한다.';

-- ── 그 요청에서 생긴 후보 전부 ─────────────────────────────────────────────────
CREATE TABLE recommendation_candidate (
    candidate_id          UUID             PRIMARY KEY,
    request_id            UUID             NOT NULL,
    place_id              UUID             NOT NULL,

    candidate_source      VARCHAR(50)      NOT NULL,
    candidate_stage       VARCHAR(30)      NOT NULL,
    eligible              BOOLEAN          NOT NULL,
    constraint_verdict    VARCHAR(10)      NOT NULL,
    violations            JSONB            NOT NULL DEFAULT '[]'::jsonb,
    unknown_facts         JSONB            NOT NULL DEFAULT '[]'::jsonb,
    constraint_confidence DOUBLE PRECISION,

    feature_values        JSONB            NOT NULL DEFAULT '{}'::jsonb,
    score_components      JSONB            NOT NULL DEFAULT '{}'::jsonb,
    pre_rank_score        DOUBLE PRECISION,
    final_score           DOUBLE PRECISION,
    original_rank         INTEGER,
    final_rank            INTEGER,
    returned              BOOLEAN          NOT NULL DEFAULT FALSE,

    reason_codes          VARCHAR(64)[]    NOT NULL DEFAULT '{}',
    warning_codes         VARCHAR(64)[]    NOT NULL DEFAULT '{}',
    fallback_mode         VARCHAR(20),
    created_at            TIMESTAMPTZ      NOT NULL,

    CONSTRAINT fk_recommendation_candidate_job
        FOREIGN KEY (request_id) REFERENCES recommendation_job (request_id) ON DELETE CASCADE,

    -- 분석 단위 그 자체. 같은 요청에서 같은 장소가 두 행이 되면 조인이 조용히 부풀어 오른다.
    CONSTRAINT uq_recommendation_candidate_request_place
        UNIQUE (request_id, place_id),

    CONSTRAINT ck_recommendation_candidate_verdict
        CHECK (constraint_verdict IN ('PASS', 'FAIL', 'UNKNOWN')),

    CONSTRAINT ck_recommendation_candidate_stage
        CHECK (candidate_stage IN ('GENERATED', 'QUALITY_FILTERED', 'HARD_FILTERED',
                                   'RANKED', 'RERANKED', 'RETURNED')),

    CONSTRAINT ck_recommendation_candidate_fallback_mode
        CHECK (fallback_mode IS NULL OR fallback_mode IN ('MODEL', 'RULE', 'BASELINE')),

    -- 🔴 하드 제약을 위반한 후보는 어떤 경로로도 화면에 나갈 수 없다.
    --    애플리케이션이 실수해도 여기서 막힌다 — 알레르기·휠체어 같은 안전 제약이다.
    CONSTRAINT ck_recommendation_candidate_fail_not_returned
        CHECK (NOT (constraint_verdict = 'FAIL' AND returned)),
    CONSTRAINT ck_recommendation_candidate_fail_not_eligible
        CHECK (NOT (constraint_verdict = 'FAIL' AND eligible)),

    -- 🔴 UNKNOWN(확인 불가)을 PASS 로 바꿔 저장하지 않는다. 판정은 판정대로 남는다.
    --    "UNKNOWN 후보를 랭킹에 태울 것인가" 는 제품 결정이라 여기(DDL)에 굳히지 않고
    --    애플리케이션 정책(ConstraintEligibilityPolicy)에 둔다 — 바꾸는 데 마이그레이션이
    --    필요하지 않아야 한다. 현재 기본값은 "태우지 않는다" 다.

    CONSTRAINT ck_recommendation_candidate_returned_shape
        CHECK (NOT returned OR (final_rank IS NOT NULL
                                AND final_score IS NOT NULL
                                AND candidate_stage = 'RETURNED'))
);

CREATE INDEX ix_recommendation_candidate_request_final_rank
    ON recommendation_candidate (request_id, final_rank);
CREATE INDEX ix_recommendation_candidate_request_returned
    ON recommendation_candidate (request_id, returned);
CREATE INDEX ix_recommendation_candidate_place
    ON recommendation_candidate (place_id);

COMMENT ON TABLE recommendation_candidate IS
    '추천 요청 한 건에서 생성된 전체 후보. Top-K 에 못 든 후보도 지우지 않는다.';
COMMENT ON COLUMN recommendation_candidate.feature_values IS
    '랭킹 시점 피처 스냅샷. 정밀 좌표·직접 식별자는 넣지 않는다 (SensitivePayloadGuard 가 막는다).';

-- ── 업무 저장과 같은 트랜잭션에 실리는 이벤트 대기열 ───────────────────────────
--
-- Outbox(**업무 데이터와 같은 트랜잭션에서 이벤트를 같은 DB 에 먼저 적어 두고,
-- 나중에 별도 프로세스가 그것을 읽어 메시지 브로커로 보내는 방식**).
-- 업무 저장은 됐는데 이벤트만 유실되는 일을 구조적으로 없앤다.
-- 🔴 이 티켓은 여기까지다. Kafka 전송은 543 범위가 아니다.
CREATE TABLE event_outbox (
    event_id         UUID         PRIMARY KEY,
    event_type       VARCHAR(64)  NOT NULL,
    event_version    INTEGER      NOT NULL,
    aggregate_type   VARCHAR(64)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    partition_key    VARCHAR(128) NOT NULL,
    payload          JSONB        NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL,
    publish_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    published_at     TIMESTAMPTZ,
    publish_attempts INTEGER      NOT NULL DEFAULT 0,
    last_error       TEXT,

    CONSTRAINT ck_event_outbox_publish_status
        CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_event_outbox_event_version
        CHECK (event_version >= 1)
);

CREATE INDEX ix_event_outbox_pending
    ON event_outbox (publish_status, occurred_at);
CREATE INDEX ix_event_outbox_type_occurred
    ON event_outbox (event_type, occurred_at);
CREATE INDEX ix_event_outbox_aggregate
    ON event_outbox (aggregate_type, aggregate_id, occurred_at);

COMMENT ON COLUMN event_outbox.event_id IS
    '이벤트 멱등 키. PK 라서 같은 event_id 를 두 번 적으려 하면 DB 가 거부한다.';
