-- S15P21E201-1614 — 「반송공원」 좌표가 남중국해(위도 19.69 · 경도 117.99)에 찍혀 있다.
--
-- 🔴 원천에서 다시 받는 것으로는 못 고친다
--    관광공사 areaBasedList2 가 이미 mapx=117.9925662504, mapy=19.6944274800 을 준다
--    (contentid 2907087, modifiedtime 20250828102001 — 저장소 bigData/data/raw/tourapi/tourapi-busan.ndjson).
--    2026-09-25 에 detailCommon2 로 다시 물어도 같은 값이었다. 원천 자체가 틀렸다.
--
-- 바른 좌표 — 오픈스트리트맵 way 1178510454 「반송공원」(leisure=park, 판 2, 2024-10-30)의 중심.
--    https://www.openstreetmap.org/way/1178510454
--    부산광역시 해운대구 반송1동 안이고, 관광공사 주소 「반송순환로 100-53 (반송동)」과 맞다.
--
-- 틀린 값일 때만 고친다. 누가 먼저 고쳤거나 원천이 바로잡혀 다시 적재됐으면 아무것도 안 한다.
-- 부산 경계 상자(위도 34.85~35.45, 경도 128.70~129.40) 밖 좌표는 2026-09-25 운영 전체에서 이 한 곳뿐이었다.
-- 이 장소에는 좌표로 계산한 표식(경사·조용함 등)이 없어 같이 고칠 것이 없다.

UPDATE place
   SET lat = 35.2207704,
       lng = 129.1619776
 WHERE source_type = 'TOURAPI'
   AND source_id = '2907087'
   AND lat BETWEEN 19 AND 20
   AND lng BETWEEN 117 AND 119;
