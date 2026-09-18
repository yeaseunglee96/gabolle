-- ══════════════════════════════════════════════════════════════════════════════
-- 절영해안산책로·해운대 그린레일웨이를 SEA_BEACH 로 옮긴다 — S15P21E201-106
-- ══════════════════════════════════════════════════════════════════════════════
--
-- TourApiCategory 가 cat3=A01010500(자연 안의 「해안 산책로/철길」 묶음, 셋)를 지금까지
-- 전부 NATURE_WALK 로 넣어 왔다. 그중 절영해안산책로(contentid 252561)·해운대
-- 그린레일웨이(미포~송정 구간, 2822343) 둘만 SEA_BEACH 로 바꾼다 — 코드 변경과 짝이다.
-- 같은 묶음의 송도반도(2614725)는 일부러 그대로 NATURE_WALK 에 둔다.
--
-- 🔴 이 UPDATE 가 실제로 몇 행을 건드리는지는 확인 못 함 — 운영 DB 읽기가 도구
--    권한에 막혀 있어 재지 못했다. TourApiPlaceLoader 가 아직 안 돌았으면 0행을
--    건드리고 지나간다 (그래도 안전하다 — 앞으로 도는 적재는 이미 SEA_BEACH 로 넣는다).
UPDATE place
   SET category = 'SEA_BEACH'
 WHERE source_type = 'TOURAPI'
   AND source_id IN ('252561', '2822343')
   AND category = 'NATURE_WALK';

UPDATE place_feature
   SET feature_key = 'SEA_BEACH'
 WHERE source_type = 'TOURAPI'
   AND source_id IN ('252561', '2822343')
   AND feature_type = 'CATEGORY_TAG'
   AND feature_key = 'NATURE_WALK';
