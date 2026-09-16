-- S15P21E201-76 — 부산 대표 명소 네 곳에 CATEGORY_TAG 표식을 붙인다.
--
-- 🔴 V20260916210000 이 네 곳을 넣으면서 place.category 만 채우고 place_feature 의
--    CATEGORY_TAG 행은 안 넣었다. 운영 실측(2026-09-16 21시)에서 네 곳 전부 표식 행이
--    0개였다. 그래서 두 칸이 어긋난 상태다.
--
--        이름으로 검색 (place.category 를 봄)      → 네 곳 다 나온다
--        갈래 집계 /places/categories               → SEA_BEACH 6 (4에서 늘었다)
--        갈래 목록 facetType=CATEGORY_TAG           → 4곳. 🔴 해운대·광안리가 없다
--
--    집계는 6인데 표식으로 뽑으면 4다. 홈 「부산 둘러보기」가 표식으로 뽑으므로
--    그 화면에 해운대·광안리가 안 뜬다. 화면이 장소 이름으로 번들 사진을 붙이는
--    구조라 사진까지 같이 사라진다.
--
-- 🔴 왜 place.category 만으로는 부족한가 — 두 칸이 서로 다른 질문에 답한다.
--    place.category 는 추천 후보를 거르고(PlaceCandidateQueryService), place_feature 의
--    CATEGORY_TAG 는 화면이 갈래별로 목록을 뽑을 때 쓴다(searchByFacet). 한쪽만 채우면
--    "추천에는 나오는데 둘러보기에는 없는" 장소가 된다.
--
-- 이 파일은 그 어긋남만 메운다. place 는 한 행도 안 건드린다.

INSERT INTO place_feature (
    place_feature_id, place_id, feature_type, feature_key, value,
    evidence_status, source_type, source_id, observed_at, source_version, created_at
)
SELECT
    seed.place_feature_id, p.place_id, 'CATEGORY_TAG', seed.feature_key,
    -- 기존 CATEGORY_TAG 행과 같은 모양이다 (관광공사 적재분 실측: value=true · ESTIMATED).
    'true'::jsonb,
    'ESTIMATED', p.source_type, p.source_id, NULL,
    'curated-core-landmarks-20260916', TIMESTAMPTZ '2026-09-16 21:40:00+09'
FROM (VALUES
    (UUID 'ac2fa726-056d-491f-8b8e-13ec369cf19d', '7913306',  'SEA_BEACH'),      -- 해운대해수욕장
    (UUID '37a17fab-53ca-4fdb-a1a8-c6388a2826dd', '8202423',  'SEA_BEACH'),      -- 광안리해수욕장
    (UUID '88154c2b-51f5-4cf7-96fc-154bc4deebc0', '21362956', 'CULTURE_TEMPLE'), -- 감천문화마을
    (UUID '9dd5ed2d-beb8-47a9-8f1e-bd09b4ebd814', '26884008', 'CULTURE_TEMPLE')  -- 범어사
) AS seed(place_feature_id, source_id, feature_key)
-- 🔴 이름이 아니라 출처로 찾는다. V20260916210000 이 넣은 그 행만 가리키므로 같은 이름의
--    다른 행(다른 적재분이나 시험이 남긴 것)에 잘못 붙지 않는다. 그 행이 없는 환경이면
--    (가드에 걸려 안 들어간 경우) 아무것도 안 넣고 지나간다 — 그것도 맞는 동작이다.
JOIN place p ON p.source_type = 'KAKAO_LOCAL' AND p.source_id = seed.source_id
-- 🔴 이미 붙어 있으면 넣지 않는다. 바다·자연 장소를 넣는 다른 마이그레이션이 이름으로
--    찾아 같은 표식을 붙이는 절을 갖고 있어서, 어느 쪽이 먼저 돌든 한 벌만 남아야 한다.
--    그래서 이 파일이 실제로 넣는 행이 4가 아니라 2로 나올 수 있다 — 정상이다.
WHERE NOT EXISTS (
    SELECT 1 FROM place_feature existing
    WHERE existing.place_id = p.place_id
      AND existing.feature_type = 'CATEGORY_TAG'
      AND existing.feature_key = seed.feature_key
)
ON CONFLICT DO NOTHING;
