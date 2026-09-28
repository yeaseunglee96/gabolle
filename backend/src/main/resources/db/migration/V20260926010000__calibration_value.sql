-- S15P21E201-1692 — 스스로 고치는 틀의 첫 사례: 실제로 머문 시간으로 갈래별 체류 시간을 고친다.
--
-- 🔴 무엇을 만드나
--    · calibration_value — 계산 작업(job)이 판(version)마다 새로 쌓는 결과 표. 지우지 않는다.
--      체류 시간이 첫 작업이고, 다음 작업(인기 점수 등)도 job 이름만 달리해 같은 표에 얹는다.
--    · calibration_stay_source — 체류 계산이 읽는 단 하나의 창구. 익명으로 줄여서 내준다.
--
-- 🔴 개인정보 — 뷰가 내주는 칸은 넷뿐이다: 갈래 · 머문 분 · 측정/추정 · 주(週).
--    사람·일정·장소 번호와 정확한 시각은 뷰 밖으로 안 나간다. 모든 사용자의 기록을 쓰되 익명 집계로만 쓴다
--    (2026-09-25 결정 — 「동의하면 개인 취향까지 가고, 동의하지 않으면 추천 품질만 잰다」). 운영에서 이 뷰를
--    읽는 것은 처리방침에 「품질 개선을 위한 익명 통계」가 나간 뒤다.
--
-- 🔴 측정과 추정
--    · MEASURED — 도착·출발을 둘 다 찍은 곳. 출발 − 도착.
--    · ESTIMATED — 출발이 없는 곳. 다음 곳 도착 − 두 곳 사이 이동 시간 − 이 곳 도착.
--      쉬는 시간·이동 지연이 섞여 부풀기 쉬워 저장만 하고 추천에는 안 쓴다(2026-09-25 결정).
--
-- 🔴 버전 V20260926010000 — 열린 MR 의 가장 큰 번호(V20260925080000)보다 위로 잡았다.

CREATE TABLE calibration_value (
    job           VARCHAR(60)      NOT NULL,
    version       INTEGER          NOT NULL,
    key           VARCHAR(60)      NOT NULL,
    basis         VARCHAR(20)      NOT NULL,
    status        VARCHAR(20)      NOT NULL,
    value         DOUBLE PRECISION,
    sample_size   INTEGER          NOT NULL,
    window_from   DATE             NOT NULL,
    window_to     DATE             NOT NULL,
    note          VARCHAR(300),
    source_commit VARCHAR(64),
    -- DB 가 채운다. 계산기(Spark)의 JVM 시간대를 타지 않게.
    computed_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT pk_calibration_value PRIMARY KEY (job, version, key, basis),
    CONSTRAINT ck_calibration_value_version CHECK (version >= 1),
    CONSTRAINT ck_calibration_value_sample CHECK (sample_size >= 0),
    CONSTRAINT ck_calibration_value_window CHECK (window_to >= window_from),
    -- 측정값만 반영 후보다. 추정값은 늘 참고(REFERENCE)로만 남는다.
    CONSTRAINT ck_calibration_value_status CHECK (
        (basis = 'MEASURED' AND status IN ('PASSED', 'HELD', 'REVOKED'))
        OR (basis = 'ESTIMATED' AND status = 'REFERENCE')),
    CONSTRAINT ck_calibration_value_passed_has_value CHECK (status <> 'PASSED' OR value IS NOT NULL)
);

COMMENT ON TABLE calibration_value IS
    'S15P21E201-1692 — 계산 작업의 결과. 판마다 쌓고 지우지 않는다. 엔진은 key 마다 가장 최근 PASSED 를 읽는다. '
    '되돌리기: 그 판을 REVOKED 로 바꾸면 다음 읽기부터 직전 PASSED 를 쓴다.';
COMMENT ON COLUMN calibration_value.status IS
    'PASSED 반영 · HELD 검사 불통과(직전 판 유지) · REVOKED 사람이 되돌림 · REFERENCE 추정값(반영 안 함)';
COMMENT ON COLUMN calibration_value.note IS '보류 이유, 또는 한 번에 바뀌는 폭을 잘라 낸 기록';

-- 엔진이 읽는 질의(job 의 key 마다 가장 최근 PASSED)를 받친다.
CREATE INDEX ix_calibration_value_passed ON calibration_value (job, key, version DESC) WHERE status = 'PASSED';

-- ── 체류 계산이 읽는 창구 ─────────────────────────────────────────────────────
-- 일정의 가장 최근 판에 있는 항목만 본다. 도착·출발(itinerary_item_actual)은 item_key 로 판을 건너 이어진다.
CREATE VIEW calibration_stay_source AS
WITH visit AS (
    SELECT v.itinerary_version_id, it.day_index, it.sequence, it.place_id, a.arrived_at, a.departed_at
      FROM itineraries i
      JOIN itinerary_versions v ON v.itinerary_id = i.itinerary_id AND v.version = i.latest_version
      JOIN itinerary_item it ON it.itinerary_version_id = v.itinerary_version_id
      JOIN itinerary_item_actual a ON a.itinerary_id = i.itinerary_id AND a.item_key = it.item_key
     WHERE a.arrived_at IS NOT NULL
), stay AS (
    SELECT p.category,
           CASE WHEN cur.departed_at IS NOT NULL THEN 'MEASURED' ELSE 'ESTIMATED' END AS source,
           CASE WHEN cur.departed_at IS NOT NULL
                THEN EXTRACT(EPOCH FROM cur.departed_at - cur.arrived_at) / 60.0
                ELSE EXTRACT(EPOCH FROM nxt.arrived_at - cur.arrived_at) / 60.0 - leg.duration_min
           END::DOUBLE PRECISION AS stay_minutes,
           date_trunc('week', cur.arrived_at AT TIME ZONE 'Asia/Seoul')::DATE AS visit_week
      FROM visit cur
      JOIN place p ON p.place_id = cur.place_id
      LEFT JOIN visit nxt ON nxt.itinerary_version_id = cur.itinerary_version_id
                         AND nxt.day_index = cur.day_index AND nxt.sequence = cur.sequence + 1
      LEFT JOIN itinerary_leg leg ON leg.itinerary_version_id = cur.itinerary_version_id
                                 AND leg.day_index = cur.day_index
                                 AND leg.from_place_id = cur.place_id AND leg.to_place_id = nxt.place_id
     WHERE p.category IS NOT NULL
       AND (cur.departed_at IS NOT NULL OR (nxt.arrived_at IS NOT NULL AND leg.duration_min IS NOT NULL))
)
SELECT category, source, stay_minutes, visit_week FROM stay WHERE stay_minutes > 0;

COMMENT ON VIEW calibration_stay_source IS
    'S15P21E201-1692 — 갈래 · 머문 분 · 측정/추정 · 주. 사람·일정·장소 번호와 정확한 시각은 내주지 않는다.';
