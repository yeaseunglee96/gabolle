-- 장소 경사의 옛 값(p90)을 걷어 낸다 — 채점기는 p50 과 견주는데 p90 이 남아 있었다.
--
-- 🔴 무엇이 틀렸나. 추천은 휠체어·유아차·큰 짐 조건에서 장소 경사(SLOPE_PERCENT)를 8.33%
--    (gabolle.recommendation.mobility.max-slope-percent, 휠체어 경사로 기준 1:12)와 견준다. 그 상한은
--    「주변 걷는 길의 가운데 값」(길이로 가중한 p50 — 절반의 길이 이보다 완만하다)을 전제로 정했다.
--    그런데 2026-09-16 에 넣은 옛 경사 행은 같은 반경의 p90(가장 가파른 10% 가 시작되는 값)이다
--    (V20260916230000__place_slope_sbiz.sql 머리말, bigData/process/place-slope.mjs). p90 은 p50 보다
--    늘 크므로, 옛 행이 남은 곳은 실제보다 가파르게 읽혀 「반드시」 조건에서 억울하게 빠졌다.
--
-- 🔴 왜 새 값을 넣어도 안 바뀌었나. 장소마다 경사 행은 하나뿐이고(uq_place_feature_unkeyed), 적재기는
--    이미 있는 행을 안 건드린다(PlaceFeatureLoader 의 ON CONFLICT DO NOTHING). 그래서 p50 적재
--    (place-slope-by-id, DERIVED_SLOPE)는 옛 행이 있는 곳마다 「이미 있어 건너뜀」으로 끝났다.
--    적재기 머리말에 손으로 먼저 지우라고 적어 뒀지만, 새로 만든 DB·다른 개발 DB 에서는 그 한 줄이 빠진다.
--
-- 그래서 여기서 지운다. 지운 뒤에는 p50 적재를 한 번 돌려야 값이 다시 생긴다 — 그 사이 경사가 없는
-- 곳은 「모른다」로 읽혀 빼지 않고 미확인 경고만 단다(틀린 값으로 빼는 것보다 낫다).
--
-- 옛 두 판(관광공사 번호판 '2026-09-16-slope-r200', 상가 번호판 '2026-09-16-slope-r200-sbiz')만 지운다.
-- 행 안에 p50 이라고 적힌 것은 판 이름이 같아도 남긴다 — 이 파일이 새 값을 지우는 일은 없어야 한다.
-- 옛 마이그레이션(V20260916230000)은 고치지 않는다 — 이미 적용된 파일을 고치면 Flyway 가 검사합에서 멈춘다.
DELETE FROM place_feature
 WHERE feature_type = 'SLOPE_PERCENT'
   AND source_version IN ('2026-09-16-slope-r200', '2026-09-16-slope-r200-sbiz')
   AND COALESCE(value ->> 'stat', '') <> 'p50';
