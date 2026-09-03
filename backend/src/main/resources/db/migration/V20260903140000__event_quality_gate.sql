-- S15P21E201-546 — 후보 → 노출 조인 축을 한 곳에서만 풀고, 품질 판정 결과를 남길 표를 만든다.
--
-- 목적(티켓 그대로): **로그는 쌓였지만 추천과 행동이 연결되지 않는 상태를 배포 전에 발견한다.**
-- 그 상태는 오류로 나타나지 않는다. 표도 멀쩡하고 응답도 200 이다. 조인해 봐야 알 수 있고,
-- 그래서 조인을 사람이 기억하는 것에 맡기지 않고 뷰와 게이트로 못 박는다.
--
-- 🔴 버전을 V20260903140000 으로 잡은 이유. 열린 브랜치에 V20260903130000(itineraries,
--    S15P21E201-313)이 있다. Flyway 는 이미 적용된 것보다 낮은 번호가 뒤늦게 오면 거부하므로,
--    저쪽이 먼저 머지돼도 이 파일이 걸리지 않도록 위로 잡았다.
--
--    🔴 2026-09-03 후기 — 이 선택이 위험을 없앤 것이 아니라 -313 쪽으로 옮겼다.
--       이 파일이 먼저 배포되어 130000 이 갇혔고 프로덕션이 502 로 내려갔다.
--       itineraries 는 V20260903150000 으로 올려 되살렸다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 조인 축을 푸는 단 하나의 자리
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 S15P21E201-352 에서 정한 것 — 추천 이벤트의 요청 축은 aggregate_id 이고,
--    request_id 칸은 aggregate 축이 다른 이벤트에서만 채운다. 그래서 요청 축으로 이으려면
--    두 칸을 함께 봐야 한다.
--
--    그 CASE 를 **여기서 한 번만** 푼다. 분석 쿼리마다 다시 쓰면 어느 날 한 곳이 빠지고,
--    빠진 것은 오류가 아니라 "행이 적게 나온다" 로 나타난다. 그건 아무도 못 본다.
--    MR !94 에서 이 자리를 546 으로 못 박았고, 이 뷰가 그 약속이다.
--
-- 🔴 placeId 를 그냥 ::uuid 로 캐스팅하면 안 된다. payload 는 JSONB 라서 uuid 가 아닌
--    문자열도 들어올 수 있고, 뷰 안에서 캐스팅이 터지면 **행 하나 때문에 뷰 전체가
--    오류가 난다.** 게이트는 잘못된 행을 세어서 알려야 하는데, 세려고 조회하면 터지는
--    구조가 된다. 그래서 모양을 먼저 검사하고, 원본 문자열도 함께 남긴다.
CREATE VIEW recommendation_exposure AS
SELECT
    e.event_id,
    e.event_type,
    e.event_version,

    -- 요청 축 — 이 COALESCE 가 이 파일의 존재 이유다.
    COALESCE(
        e.request_id,
        CASE WHEN upper(e.aggregate_type) = 'RECOMMENDATION' THEN e.aggregate_id END
    )                                                          AS request_id,

    -- 모양이 맞을 때만 uuid 로 준다. 아니면 NULL 이고, 원본은 place_id_raw 에 남는다.
    CASE
        WHEN e.payload ->> 'placeId' ~
             '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        THEN (e.payload ->> 'placeId')::uuid
    END                                                        AS place_id,
    e.payload ->> 'placeId'                                    AS place_id_raw,

    CASE WHEN e.payload ->> 'finalRank' ~ '^[0-9]{1,9}$'
         THEN (e.payload ->> 'finalRank')::integer END          AS final_rank,
    e.payload ->> 'finalRank'                                  AS final_rank_raw,

    e.payload ->> 'sourceScreen'                               AS source_screen,
    e.payload ->> 'fallbackMode'                               AS fallback_mode,
    e.payload ->> 'modelVersion'                               AS model_version,
    e.payload ->> 'featureVersion'                             AS feature_version,
    e.payload ->> 'ontologyVersion'                            AS ontology_version,
    e.payload ->> 'datasetVersion'                             AS dataset_version,
    e.payload ->> 'policyVersion'                              AS policy_version,

    e.user_id,
    e.trip_id,
    e.producer,
    e.occurred_at,
    e.received_at,
    e.seq,
    e.payload
FROM event_outbox e
WHERE upper(e.event_type) = 'RECOMMENDATION_IMPRESSION';

COMMENT ON VIEW recommendation_exposure IS
    'S15P21E201-546 — 노출 이벤트를 후보와 이을 수 있는 모양으로 낸다. 🔴 요청 축(request_id 와 aggregate_id 를 합치는 CASE)을 푸는 자리는 이 뷰 하나다. 분석 쿼리는 event_outbox 를 직접 읽지 않고 이 뷰를 읽는다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 품질 판정 결과 — 일자 · datasetVersion 별로 남긴다 (티켓 작업 내용 5)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 왜 남기는가. 게이트가 실패했다는 사실만으로는 "언제부터 나빠졌는가" 를 못 본다.
-- 비율을 날짜별로 남겨 두면 어느 배포에서 꺾였는지가 보인다.
CREATE TABLE event_quality_report (
    report_id                      UUID        PRIMARY KEY,
    report_date                    DATE        NOT NULL,

    -- 🔴 어느 데이터 판을 검사한 결과인가. 이것이 없으면 비율만 남고 무엇을 재었는지가
    --    사라진다 (FR-REC-12 버전 추적).
    dataset_version                VARCHAR(100) NOT NULL,

    -- ── 센 것 ──
    events_total                   BIGINT      NOT NULL,
    impressions_total              BIGINT      NOT NULL,
    candidates_total               BIGINT      NOT NULL,

    -- ── 비율 (0~1) ──
    schema_valid_rate              DOUBLE PRECISION NOT NULL,
    duplicate_rate                 DOUBLE PRECISION NOT NULL,
    request_id_missing_rate        DOUBLE PRECISION NOT NULL,
    candidate_feature_missing_rate DOUBLE PRECISION NOT NULL,

    -- ── 실패로 처리하는 위반 (티켓 작업 내용 3·4) ──
    orphan_impressions             BIGINT      NOT NULL,
    rank_mismatches                BIGINT      NOT NULL,
    version_missing                BIGINT      NOT NULL,
    pii_violations                 BIGINT      NOT NULL,
    fail_verdict_exposed           BIGINT      NOT NULL,

    -- 🔴 통과 여부를 값으로 남긴다. 종료 코드는 사라지고 이 행은 남는다.
    passed                         BOOLEAN     NOT NULL,
    failure_summary                TEXT,

    created_at                     TIMESTAMPTZ NOT NULL,

    -- 같은 날 같은 판을 두 번 재면 뒤에 잰 것이 맞다. 덮어쓰도록 유일 제약을 둔다.
    CONSTRAINT uq_event_quality_report_date_dataset
        UNIQUE (report_date, dataset_version),

    CONSTRAINT ck_event_quality_report_counts
        CHECK (events_total >= 0 AND impressions_total >= 0 AND candidates_total >= 0
               AND orphan_impressions >= 0 AND rank_mismatches >= 0
               AND version_missing >= 0 AND pii_violations >= 0
               AND fail_verdict_exposed >= 0),

    CONSTRAINT ck_event_quality_report_rates
        CHECK (schema_valid_rate BETWEEN 0 AND 1
               AND duplicate_rate BETWEEN 0 AND 1
               AND request_id_missing_rate BETWEEN 0 AND 1
               AND candidate_feature_missing_rate BETWEEN 0 AND 1),

    -- 🔴 위반이 하나라도 있으면 통과라고 적을 수 없다. "초록인데 위반이 있다" 는 행을
    --    DB 가 거부한다 — 게이트 코드가 실수해도 리포트가 거짓말을 하지 못한다.
    --
    --    request_id_missing_rate 도 같이 본다. 티켓 완료 기준이 "버전 또는 requestId 누락이
    --    조용히 통과하지 않는다" 이므로, 그 둘은 비율이 아니라 통과를 막는 값이다.
    CONSTRAINT ck_event_quality_report_passed_means_clean
        CHECK (NOT passed
               OR (orphan_impressions = 0 AND rank_mismatches = 0 AND version_missing = 0
                   AND pii_violations = 0 AND fail_verdict_exposed = 0
                   AND request_id_missing_rate = 0)),

    -- 실패는 이유 없이 남지 않는다.
    CONSTRAINT ck_event_quality_report_failure_has_reason
        CHECK (passed OR failure_summary IS NOT NULL)
);

CREATE INDEX ix_event_quality_report_date
    ON event_quality_report (report_date DESC);

COMMENT ON TABLE event_quality_report IS
    'S15P21E201-546 품질 게이트의 판정 결과. 🔴 passed=true 인데 위반 건수가 0 이 아닌 행은 DB 가 거부한다.';
