-- S15P21E201-1700 — 보정 틀 두 번째 작업: 실제 이동 시간으로 수단별 배율(실제 ÷ 어림)을 잰다.
--
-- 🔴 왜 칸이 하나 느는가 (2026-09-26 사용자 결정 — 「원래 어림 분」 칸)
--    구간의 이동 시간(duration_min)은 한 자리에서 만들어져 그대로 저장되고, 같은 값이 화면의 「이동 25분」과 시각 깔기에
--    쓰인다. 배율을 적용하면 이 칸은 고친 값이 된다. 그 칸만 남기면 다음 계산이 실제를 「이미 고친 값」과 견주게 되어
--    배율이 겹쳐 곱해지거나 1 로 수렴한다. 그래서 엔진이 처음 어림한 값을 옆 칸에 남긴다.
--    · 새 구간: 엔진이 어림하면 늘 채운다(보정이 꺼져 있어도 — 그때는 duration_min 과 같다).
--    · 옛 구간: 비어 있다. 비어 있으면 「이동 시간 = 원래 어림」으로 읽는다 — 이 칸 전에는 보정이 없었다.
--
-- 🔴 창구 뷰가 내주는 칸은 다섯뿐이다 — 수단 · 거리 구간 · 어림 분 · 실제 분 · 주(週).
--    사람·일정·장소 번호와 정확한 시각은 뷰 밖으로 안 나간다(체류 창구와 같은 원칙).
--
-- 🔴 실제 이동 = 같은 날 실제로 다녀간 순서(도착 시각 순)에서 앞 곳 출발 → 다음 곳 도착.
--    · 계획 순서가 아니다. 건너뛴 곳은 도착이 없어 사이에 안 들어간다.
--    · 앞 곳 출발이 비어 있으면 그 구간은 뺀다 — 지어내지 않는다.
--    · 어림은 계획에서 붙어 있던 두 곳 사이에만 있다. 실제로 건너뛰거나 순서를 바꿔 다닌 쌍(A→C)은 어림이 없어 빠진다.
--
-- 🔴 버전 V20260926020000 — 열린 MR 의 가장 큰 번호(V20260926010000, !1690)보다 위.

ALTER TABLE itinerary_leg ADD COLUMN uncalibrated_duration_min INTEGER;
ALTER TABLE itinerary_leg ADD CONSTRAINT ck_itinerary_leg_uncalibrated_duration
    CHECK (uncalibrated_duration_min IS NULL OR uncalibrated_duration_min >= 0);

COMMENT ON COLUMN itinerary_leg.uncalibrated_duration_min IS
    'S15P21E201-1700 — 엔진이 처음 어림한 이동 분(보정 전). duration_min 은 보정한 값이다. 비어 있으면 duration_min 이 곧 어림이다(이 칸 전의 구간).';

CREATE VIEW calibration_travel_source AS
WITH visit AS (
    SELECT v.itinerary_version_id, it.day_index, it.place_id, a.arrived_at, a.departed_at
      FROM itineraries i
      JOIN itinerary_versions v ON v.itinerary_id = i.itinerary_id AND v.version = i.latest_version
      JOIN itinerary_item it ON it.itinerary_version_id = v.itinerary_version_id
      JOIN itinerary_item_actual a ON a.itinerary_id = i.itinerary_id AND a.item_key = it.item_key
     WHERE a.arrived_at IS NOT NULL
), walked AS (
    -- 같은 날, 실제로 도착한 순서로 줄 세워 바로 앞 곳을 붙인다.
    SELECT visit.*,
           lag(place_id) OVER day_order AS prev_place_id,
           lag(departed_at) OVER day_order AS prev_departed_at
      FROM visit
    WINDOW day_order AS (PARTITION BY itinerary_version_id, day_index ORDER BY arrived_at)
)
SELECT leg.travel_mode AS mode,
       CASE WHEN leg.distance_m IS NULL THEN 'UNKNOWN'
            WHEN leg.distance_m < 1000 THEN 'UNDER_1KM'
            WHEN leg.distance_m < 3000 THEN '1_3KM'
            WHEN leg.distance_m < 10000 THEN '3_10KM'
            ELSE 'OVER_10KM' END AS distance_band,
       COALESCE(leg.uncalibrated_duration_min, leg.duration_min)::DOUBLE PRECISION AS estimated_minutes,
       (EXTRACT(EPOCH FROM w.arrived_at - w.prev_departed_at) / 60.0)::DOUBLE PRECISION AS actual_minutes,
       date_trunc('week', w.arrived_at AT TIME ZONE 'Asia/Seoul')::DATE AS visit_week
  FROM walked w
  JOIN itinerary_leg leg ON leg.itinerary_version_id = w.itinerary_version_id
                        AND leg.day_index = w.day_index
                        AND leg.from_place_id = w.prev_place_id
                        AND leg.to_place_id = w.place_id
 WHERE w.prev_departed_at IS NOT NULL
   AND w.arrived_at > w.prev_departed_at
   AND COALESCE(leg.uncalibrated_duration_min, leg.duration_min) > 0;

COMMENT ON VIEW calibration_travel_source IS
    'S15P21E201-1700 — 수단 · 거리 구간 · 어림 분(보정 전) · 실제 분 · 주. 사람·일정·장소 번호와 정확한 시각은 내주지 않는다.';
