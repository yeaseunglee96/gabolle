-- ══════════════════════════════════════════════════════════════════════════════
-- 카페의 place.category 를 FOOD 에서 CAFE_HEALING 으로 가른다 — S15P21E201-106
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 후보를 거르는 코드(PlaceCandidateQueryService)는 place.category 한 칸만 글자 그대로
-- 비교한다. SbizPlaceLoader 는 지금까지 카페도 이 칸에 FOOD 를 넣어 왔다 — 그래서
-- "카페·힐링" 을 고른 사용자의 후보가 0건이었고(카페는 다 FOOD 아래 숨어 있었다),
-- "맛집" 을 고른 사용자에게는 카페가 섞여 나왔다.
--
-- 둘 중 하나만 고를 수 있다 — place.category 는 한 칸이라 두 갈래를 동시에 담지 못한다.
-- 음식점 추천에 카페가 섞이는 쪽이 더 나쁘다는 판단으로 카페를 FOOD 에서 뺀다
-- (place_feature 의 CATEGORY_TAG:FOOD 태그는 그대로 둔다 — 후보 필터는 이제 그것을
-- 안 보므로 지울 이유가 없다. SbizPlaceLoader 코드 변경과 짝이다).
--
-- 🔴 이 UPDATE 가 실제로 몇 행을 건드리는지는 확인 못 함 — 운영 DB 읽기가 도구
--    권한에 막혀 있어 재지 못했다. 상가정보 전체 적재가 아직 안 끝났을 수 있고,
--    그 경우 이 마이그레이션은 0행을 건드리고 지나간다 (그래도 안전하다 — 앞으로
--    들어오는 행은 SbizPlaceLoader 가 이미 CAFE_HEALING 으로 넣는다).
UPDATE place
   SET category = 'CAFE_HEALING'
 WHERE source_type = 'SBIZ'
   AND category = 'FOOD'
   AND place_id IN (
       SELECT place_id FROM place_feature
        WHERE source_type = 'SBIZ'
          AND feature_type = 'CATEGORY_TAG'
          AND feature_key = 'CAFE_HEALING'
   );
