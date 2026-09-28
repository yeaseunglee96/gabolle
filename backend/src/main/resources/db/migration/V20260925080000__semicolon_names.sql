-- S15P21E201-1637 — 이름의 세미콜론(옛 이름·다른 이름)은 한 이름만 쓴다.
--
-- 🔴 왜
--    오픈스트리트맵은 옛 이름·다른 이름을 세미콜론으로 이어 적고, 상가 자료에도 같은 모양이 섞여 들어와 화면에 그대로
--    나갔다. 2026-09-25 운영 5곳:
--      남나리전복;엠아이알오 (상가) · 오즈;Oddz (상가) · 구;경포횟집 (상가) · 선모텔;코리아나모텔 (오픈스트리트맵)
--      · 삼구유통광장마트;대성당 (오픈스트리트맵)
--
-- 규칙(적재기의 PlaceNames 와 같다): 앞 이름을 쓴다. 앞 이름이 한 글자면 뒤 이름 — 「구;경포횟집」의 「구」는 「예전(舊)」.
-- 「삼구유통광장마트;대성당」은 원자료가 마트(shop=supermarket)와 성당(amenity=place_of_worship)을 한 점에 합친 것이라
-- 이름으로 못 푼다 — 「숨김」(S15P21E201-1636)으로 뺀다. 적재기도 이런 점은 이제 넣지 않는다.
--
-- 되돌리기(옛 이름):
--    UPDATE place SET name_ko = '남나리전복;엠아이알오' WHERE place_id = '508af92d-4576-3f6f-8704-28b4ae14dc49';
--    UPDATE place SET name_ko = '오즈;Oddz'             WHERE place_id = 'c37e852d-2b8c-37ef-b6a1-9eb103e813ff';
--    UPDATE place SET name_ko = '구;경포횟집'           WHERE place_id = 'ef0270eb-046e-3830-b91d-fc3f9ce7167e';
--    UPDATE place SET name_ko = '선모텔;코리아나모텔'   WHERE place_id = '5b4c0504-c671-33d5-ab4d-9d67a1bad915';
--    UPDATE place SET curation_status = 'CURATED'
--     WHERE place_id = '1f94aa60-8c2a-3f5f-8aef-2cd025fcc882' AND curation_status = 'HIDDEN';

-- 1. 두 가게가 한 점에 합쳐진 곳 — 숨긴다(이름은 그대로 둔다: 어느 쪽이 주인인지 모른다)
UPDATE place
   SET curation_status = 'HIDDEN'
 WHERE place_id = '1f94aa60-8c2a-3f5f-8aef-2cd025fcc882'   -- 삼구유통광장마트;대성당 (OSM 368669907)
   AND curation_status = 'CURATED';

-- 2. 나머지는 한 이름만
UPDATE place
   SET name_ko = CASE
           WHEN char_length(btrim(split_part(name_ko, ';', 1))) = 1
                AND btrim(split_part(name_ko, ';', 2)) <> ''
               THEN btrim(split_part(name_ko, ';', 2))
           ELSE btrim(split_part(name_ko, ';', 1))
       END
 WHERE name_ko LIKE '%;%'
   AND btrim(split_part(name_ko, ';', 1)) <> ''
   AND place_id <> '1f94aa60-8c2a-3f5f-8aef-2cd025fcc882';
