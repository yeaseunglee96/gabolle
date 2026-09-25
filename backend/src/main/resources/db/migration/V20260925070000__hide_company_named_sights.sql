-- S15P21E201-1636 — 명소 갈래의 회사 이름(주식회사·(주)) 6곳을 추천·검색에서 뺀다. 지우지 않고 「숨김」으로.
--
-- 🔴 왜
--    관광공사가 체험·쇼핑 업체를 법인 이름 그대로 올려(「주식회사뷰티홀릭」 등) 명소 갈래로 들어왔고, 최근 7일 운영
--    일정에 1곳이 2번 나왔다(2026-09-25). 사용자 결정 — 추천 후보에서 빼되 가장 단순하게, 되돌릴 수 있게.
--
-- 🔴 왜 이 방식
--    장소를 「찾아 주는」 조회(추천 후보·검색·근처·둘러보기)는 모두 CURATED 만 본다(PlaceRepository). 상태만 바꾸면
--    조회는 하나도 안 고친다. 번호로 여는 조회(상세·옛 일정)는 그대로 열린다. 되돌리기는 아래 UPDATE 한 줄이다.
--
-- 🔴 안 뺀 것 — 이름이 회사 같아도 진짜 명소·가게다
--    부산은행 금융역사관(박물관) · (주)신라호텔 등 숙소 3곳 · 주식회사세연정(밥집). 삼구유통광장마트;대성당은
--    세미콜론 이름 건에서 따로 본다(마트와 성당이 한 점에 합쳐진 원자료 — 이 「숨김」을 쓴다).
--
-- 되돌리기:
--    UPDATE place SET curation_status = 'CURATED'
--     WHERE place_id IN ('3e826cfd-2647-34d7-9289-3bb8348512ae', '8c2506f0-021e-30b5-a78c-03fd1f6f8656',
--                        'c3489c06-4f6e-3dd0-89a3-06f3c7c4dfc3', '23e967ae-b501-306e-a397-ebd2909e4812',
--                        '0e6e0e0c-58c7-3610-ba84-89bbefd2fd16', 'a5e56a96-7541-3702-af09-4627162cd6aa')
--       AND curation_status = 'HIDDEN';

ALTER TABLE place DROP CONSTRAINT ck_place_curation_status;
ALTER TABLE place
    ADD CONSTRAINT ck_place_curation_status
        CHECK (curation_status IN ('CURATED', 'USER_SUBMITTED', 'MERGED', 'HIDDEN'));

-- 2026-09-25 운영 — 없는 번호는 0건으로 지나간다(다른 DB).
UPDATE place
   SET curation_status = 'HIDDEN'
 WHERE curation_status = 'CURATED'
   AND place_id IN (
       '3e826cfd-2647-34d7-9289-3bb8348512ae',  -- 주식회사아래모래 (TOURAPI 3511011, 도시)
       '8c2506f0-021e-30b5-a78c-03fd1f6f8656',  -- 주식회사감천아울 (TOURAPI 3510987)
       'c3489c06-4f6e-3dd0-89a3-06f3c7c4dfc3',  -- 주식회사뷰티홀릭 (TOURAPI 3511057)
       '23e967ae-b501-306e-a397-ebd2909e4812',  -- 주식회사어반힐링 (TOURAPI 3511018)
       '0e6e0e0c-58c7-3610-ba84-89bbefd2fd16',  -- 주식회사피알아이피 (TOURAPI 3511071)
       'a5e56a96-7541-3702-af09-4627162cd6aa'   -- (주)엘지종합전시관 (OSM 368898422)
   );
