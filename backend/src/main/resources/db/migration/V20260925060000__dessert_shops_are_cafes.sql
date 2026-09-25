-- S15P21E201-1635 — 디저트 가게는 밥집이 아니라 카페다.
--
-- 🔴 왜
--    상가 자료가 젤라또·아이스크림·빵집을 「음식점」으로 넣어 갈래가 밥집(FOOD)이었다. 일정 조립은 밥집을 끼니 칸에
--    앉히므로 「점심으로 젤라또」가 됐다 — 최근 4일 운영 일정에 12번, 그중 8번이 점심·저녁 시간(2026-09-25).
--    음식 종류 표식이 디저트(CAFE_DESSERT) 하나뿐인 밥집이 대상이다 — 그날 운영 9곳(젤라또부 둘 · 젤라또조이 둘 ·
--    배스킨라빈스광안역점 · 파리바게뜨 둘 · 뚜레쥬르 둘). 다른 음식 종류가 섞인 곳(디저트도 파는 식당)은 그대로 둔다.
--
--    다음 적재 때 다시 밥집으로 들어오는 것은 코드가 막는다(일정 조립이 이런 밥집을 카페로 읽는다).
--
-- 되돌리기 — 2026-09-25 운영 9곳:
--    UPDATE place SET category = 'FOOD' WHERE place_id IN (
--      '0ad58e59-7614-366a-9887-7eede177c194', 'fe0a63e9-ceaf-354e-838f-35d5ebb1e894',
--      'a788ea49-bb63-3626-ab5e-5e86be860ef5', '496e54d2-cda9-3821-bf49-575f7dcc20f8',
--      '846b4807-7ab6-3eff-823d-feedd8ae3884', '6dd98ab1-e0c6-3aa5-baf5-d78565a3bea2',
--      '2c13fd54-3917-35da-b177-7f5ef78dda50', 'aa10eca6-d1b4-3947-a4e9-7e6b6544c41e',
--      '6f88919c-d9ee-301f-a1d2-e16e2b050dfa');
--    그리고 같은 곳의 CATEGORY_TAG 표식 feature_key 를 'CAFE_HEALING' → 'FOOD'.

CREATE TEMP TABLE dessert_only ON COMMIT DROP AS
SELECT f.place_id
  FROM place_feature f
 WHERE f.feature_type = 'CUISINE_TAG'
 GROUP BY f.place_id
HAVING bool_and(f.feature_key = 'CAFE_DESSERT');

UPDATE place p
   SET category = 'CAFE_HEALING'
  FROM dessert_only d
 WHERE p.place_id = d.place_id
   AND p.category = 'FOOD';

-- 갈래 표식도 같이 — 장소 칸과 표식이 어긋나면 갈래로 거르는 조회와 채점이 서로 다른 답을 낸다.
-- 이미 카페 표식이 있는 곳은 건드리지 않는다(같은 장소·같은 표식이 둘이 된다).
UPDATE place_feature f
   SET feature_key = 'CAFE_HEALING'
  FROM dessert_only d
 WHERE f.place_id = d.place_id
   AND f.feature_type = 'CATEGORY_TAG'
   AND f.feature_key = 'FOOD'
   AND NOT EXISTS (SELECT 1
                     FROM place_feature g
                    WHERE g.place_id = f.place_id
                      AND g.feature_type = 'CATEGORY_TAG'
                      AND g.feature_key = 'CAFE_HEALING');
