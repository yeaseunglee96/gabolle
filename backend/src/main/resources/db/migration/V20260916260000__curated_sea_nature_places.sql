-- ══════════════════════════════════════════════════════════════════════════════
-- 바다·자연 장소를 손으로 골라 넣는다 — S15P21E201-1025
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 왜 이 파일인가. 온보딩에서 갈래를 고른 답 35건 중 24건(69%)이 「바다」를 골랐는데,
--    2026-09-16 운영 실측에서 category 가 SEA_BEACH 인 장소가 **4곳**이었다
--    (감지해변 · 절영해안산책로 · 해운대 그린레일웨이 · 부산 송도해수욕장).
--    해운대도 광안리도 없다. 추천 코드는 멀쩡한데 사용자 대부분이 「맞는 장소가 없습니다」
--    로 끝난다. 코드로는 못 푼다 — 장소를 넣어야 풀린다.
--
--    같은 날 운영 갈래 분포: FOOD 2,329 · CULTURE_TEMPLE 162 · (비어있음) 94 ·
--    CITY 47 · CAFE_HEALING 26 · NATURE_WALK 21 · SEA_BEACH 4 (합 2,683).
--
-- 🔴 두 곳을 같이 채운다. 하나만 채우면 화면이 안 바뀐다.
--      · place.category             — 후보를 거르는 칸. PlaceCandidateQueryService 가 이것만 본다
--      · place_feature CATEGORY_TAG — 채점기가 취향과 겹치는지 보는 표식
--    BaselineCandidateTranslator 가 사용자의 CATEGORY 답(SEA_BEACH 등)을 place.category 와
--    **글자 그대로** 비교하므로 낱말이 정확히 같아야 한다. 또 category 가 비어 있는 장소는
--    요청이 갈래를 좁혔는지와 무관하게 **언제나** 후보에서 빠진다(S15P21E201-899).
--
-- ── 출처: 한국관광공사 TourAPI (공공누리) ─────────────────────────────────────
--
-- 🔴 카카오 Local API 를 쓰지 않았다. 팀 규칙이 그 응답의 저장을 금지한다
--    (bigData/CLAUDE.md 1절 · config/sources.json 의 banned 에 kakao-map).
--    TourAPI 는 공공누리라 출처를 밝히면 저장·재배포할 수 있다. 같은 장소를 같은 품질로
--    받을 수 있으므로 금지된 출처를 쓸 이유가 없었다.
--
-- 🔴 왜 지금까지 관광공사 자료에 해운대가 없었나 — **수집이 areaCode=6 으로 걸러서다.**
--    areaBasedList2 에 areaCode=6(부산)을 주면 areacode 칸이 **비어 있는** 항목이 통째로
--    빠진다. 해운대해수욕장(contentid 126081)·광안리해수욕장(126078)이 정확히 그 경우다.
--    searchKeyword2 로 이름을 직접 물으면 둘 다 나온다. 즉 관광공사에 없었던 것이 아니라
--    **우리 수집기가 못 본 것**이다. TourApiCategory 의 javadoc 이 «관광공사의 부산 관광지
--    목록에 해운대·광안리가 없다» 고 적어 둔 것은 이 거르기까지는 못 본 관찰이다.
--    🔴 수집기 자체를 고치는 것은 이 파일의 몫이 아니다 — 따로 티켓으로 간다.
--
-- ── 갈래를 사람이 정했다 ─────────────────────────────────────────────────────
--
-- searchKeyword2 응답은 cat1/cat3 가 비어 오는 항목이 많아 TourApiCategory 의 자동 대응을
-- 쓸 수 없었다. 그래서 18곳의 갈래는 **손으로 정했다.** 그래서 표식의 근거는
-- ESTIMATED 다 (적재기 TourApiPlaceLoader 가 CATEGORY_TAG 에 쓰는 값과 같다).
--
--   SEA_BEACH(12) — 해수욕장과, 「바다를 보러 가는 곳」이 목적인 해안 명소.
--                  이기대·태종대·오륙도·몰운대·청사포·흰여울문화마을이 여기다.
--   NATURE_WALK(5) — 강·수원지·숲·생태공원.
--   CULTURE_TEMPLE(1) — 해동용궁사. 바닷가 절이지만 절은 절이다. 🔴 운영에 이 이름이
--                  **아예 없었다**. 부산 대표 명소가 빠져 있어 같이 넣는다.
--
-- 🔴 name_en 을 비워 둔다. searchKeyword2(국문)는 영문명을 주지 않는다. 지어내지 않는다 —
--    필요해지면 관광공사 영문 서비스(EngService2)를 부르는 별도 작업이다.
--
-- ── 중복을 만들지 않는다 ─────────────────────────────────────────────────────
--
-- 이름(띄어쓰기 무시) 또는 출처 짝이 이미 있으면 넣지 않는다. 🔴 V20260916210000
-- (curated_core_busan_landmarks)이 해운대해수욕장·광안리해수욕장을 **카카오 출처로 먼저**
-- 넣었다 — 2026-09-16 11:47 운영 적용 완료. 그래서 아래 두 줄은 0행을 넣고 지나간다.
-- 정상이다. 이름을 그 파일과 **똑같이** 맞춰 두었기에 걸러진다.
-- 그래도 표식과 사진은 아래 2·3 절이 **그 행에** 붙여 준다.
--
-- 🔴 번호를 22만에서 26만으로 옮겼다 (2026-09-16 21시).
--    22만으로 올렸는데 뒤 번호인 23만(place_slope_sbiz)이 **먼저 머지돼 운영에 적용됐다.**
--    이 저장소에는 Flyway 의 out-of-order 설정이 없어, 적용된 최대 번호보다 작은 파일은
--    **거부되고 서버가 안 뜬다.** 운영 적용 최대가 23만이 된 이상 22만은 영원히 못 들어간다.
--    그래서 아직 머지 안 된 24만·25만보다도 뒤인 26만으로 옮겼다.
--    🔴 교훈: 번호는 「올린 순서」가 아니라 **「머지되는 순서」**로 정해진다.

-- ── 1. 장소 ────────────────────────────────────────────────────────────────
INSERT INTO place (
    place_id, name_ko, name_en, category, address, lat, lng, created_at,
    source_type, source_id, collected_at, observed_at, dataset_version,
    photo_url, photo_source
)
SELECT
    seed.place_id, seed.name_ko, NULL, seed.category, seed.address,
    seed.lat, seed.lng, TIMESTAMPTZ '2026-09-16 20:00:00+09',
    'TOURAPI', seed.source_id, TIMESTAMPTZ '2026-09-16 20:00:00+09', NULL, 'tourapi-curated-20260916',
    seed.photo_url,
    CASE WHEN seed.photo_url IS NOT NULL THEN '한국관광공사 공공누리 제1유형' END
FROM (VALUES
    (UUID '10509eed-dd9f-49f2-a1f7-000126081001', '해운대해수욕장', 'SEA_BEACH', '부산광역시 해운대구 해운대해변로 264', 35.1590840227, 129.1602785648, '126081', 'https://tong.visitkorea.or.kr/cms/resource/47/4105447_image2_1.jpg'),
    (UUID 'b3a4b379-f0ed-40f5-a1c8-000126078001', '광안리해수욕장', 'SEA_BEACH', '부산광역시 수영구 광안해변로 219 (광안동)', 35.1531932737, 129.1189760936, '126078', 'https://tong.visitkorea.or.kr/cms/resource/45/3311245_image2_1.jpg'),
    (UUID '148d72d0-4748-43b0-a650-000126080001', '송정해수욕장', 'SEA_BEACH', '부산광역시 해운대구 송정동 712-2', 35.178728, 129.199722, '126080', 'https://tong.visitkorea.or.kr/cms/resource/22/3495922_image2_1.jpg'),
    (UUID '9e41ac6e-440d-4c42-acf7-000126079001', '다대포해수욕장', 'SEA_BEACH', '부산광역시 사하구 몰운대1길 14', 35.046247, 128.963151, '126079', 'https://tong.visitkorea.or.kr/cms/resource/15/3497115_image2_1.jpg'),
    (UUID '4b30587b-b346-42f8-a0ff-000126098001', '일광해수욕장', 'SEA_BEACH', '부산광역시 기장군 일광읍 삼성리', 35.2598, 129.234, '126098', 'https://tong.visitkorea.or.kr/cms/resource/94/3495494_image2_1.jpg'),
    (UUID 'dd1823f6-d9b6-4f42-abdb-001939570001', '임랑해수욕장', 'SEA_BEACH', '부산광역시 기장군 장안읍 임랑리', 35.3159730180485, 129.262056351708, '1939570', 'https://tong.visitkorea.or.kr/cms/resource/79/3495479_image2_1.jpg'),
    (UUID '50b95fb1-6fb8-4c93-a3eb-001945309001', '이기대', 'SEA_BEACH', '부산광역시 남구 이기대공원로 68', 35.115449, 129.123566, '1945309', 'https://tong.visitkorea.or.kr/cms/resource/11/4096311_image2_1.jpg'),
    (UUID 'ba6cf0cb-85e3-4f9c-ad00-000126658001', '태종대', 'SEA_BEACH', '부산광역시 영도구 전망로 24 (동삼동)', 35.05969491904282, 129.07980569117214, '126658', 'https://tong.visitkorea.or.kr/cms/resource/83/3506383_image2_1.jpg'),
    (UUID 'ece96137-82de-4e79-ace6-000126088001', '오륙도 (부산 국가지질공원)', 'SEA_BEACH', '부산광역시 남구 오륙도로 137', 35.0920024009764, 129.126919645422, '126088', 'https://tong.visitkorea.or.kr/cms/resource/20/3496820_image2_1.jpg'),
    (UUID 'bd25ec0b-b601-4215-a85e-000129602001', '청사포', 'SEA_BEACH', '부산광역시 해운대구 중동', 35.1604039113018, 129.192189444504, '129602', 'https://tong.visitkorea.or.kr/cms/resource/30/3495930_image2_1.jpg'),
    (UUID '58c4aa95-087c-46e4-a557-002684712001', '흰여울문화마을', 'SEA_BEACH', '부산광역시 영도구 영선동4가', 35.0783, 129.0453, '2684712', 'https://tong.visitkorea.or.kr/cms/resource/74/3495874_image2_1.jpg'),
    (UUID '4533d22c-2f77-4441-aff8-002614721001', '몰운대(부산)', 'SEA_BEACH', '부산광역시 사하구 다대동', 35.0405, 128.9701, '2614721', 'https://tong.visitkorea.or.kr/cms/resource/30/3506330_image2_1.jpg'),
    (UUID '937eebc9-9200-4a6c-af2b-001338947001', '삼락생태공원', 'NATURE_WALK', '부산 사상구 삼락동 29-46', 35.1691043434193, 128.973176285884, '1338947', 'https://tong.visitkorea.or.kr/cms/resource/90/3497090_image2_1.jpg'),
    (UUID '523392a1-98f6-419c-a40d-000127974001', '을숙도 공원', 'NATURE_WALK', '부산광역시 사하구 낙동남로 1240 (하단동)', 35.10449270271807, 128.94597747957368, '127974', 'https://tong.visitkorea.or.kr/cms/resource/21/3497121_image2_1.jpg'),
    (UUID '864ef58b-e185-40d9-a972-002661475001', '회동수원지(회동수원지 둘레길)', 'NATURE_WALK', '부산광역시 금정구 오륜동 171', 35.2573, 129.1108, '2661475', 'https://tong.visitkorea.or.kr/cms/resource/56/3552456_image2_1.jpg'),
    (UUID '090f0346-d014-4d3b-a6be-002718728001', '성지곡수원지', 'NATURE_WALK', '부산광역시 부산진구 새싹로 295', 35.1853287241433, 129.0423888247, '2718728', 'https://tong.visitkorea.or.kr/cms/resource/64/3496964_image2_1.jpg'),
    (UUID '82ceb20c-dd04-4303-af3c-002487931001', '화명생태공원', 'NATURE_WALK', '부산광역시 북구 생태공원길 125 (화명동)', 35.226662731912064, 129.00454069619033, '2487931', 'https://tong.visitkorea.or.kr/cms/resource/60/3497060_image2_1.jpg'),
    (UUID 'ce91b390-e725-455f-ac39-000126848001', '해동용궁사', 'CULTURE_TEMPLE', '부산광역시 기장군 기장읍 용궁길 86', 35.1882428912, 129.2235301928, '126848', NULL)
) AS seed(place_id, name_ko, category, address, lat, lng, source_id, photo_url)
WHERE NOT EXISTS (
    SELECT 1
    FROM place existing
    WHERE LOWER(REPLACE(existing.name_ko, ' ', '')) = LOWER(REPLACE(seed.name_ko, ' ', ''))
       OR (existing.source_type = 'TOURAPI' AND existing.source_id = seed.source_id)
)
ON CONFLICT (place_id) DO NOTHING;

-- ── 2. 갈래 표식 ───────────────────────────────────────────────────────────
--
-- 🔴 위에서 넣은 행이 아니라 **이름으로 찾은 행**에 붙인다. 그래야 해운대·광안리가
--    카카오 출처로 먼저 들어와 있어도 그 행에 표식이 붙는다. 그 파일은 place 만 넣고
--    표식은 안 넣기 때문에, 이 절이 없으면 두 곳은 채점에서 갈래가 없는 장소가 된다.
INSERT INTO place_feature (
    place_feature_id, place_id, feature_type, feature_key, value,
    evidence_status, source_type, source_id, observed_at, source_version, created_at
)
SELECT
    seed.place_feature_id, p.place_id, 'CATEGORY_TAG', seed.category, 'true'::jsonb,
    'ESTIMATED', 'TOURAPI', seed.source_id, NULL, 'tourapi-curated-20260916', TIMESTAMPTZ '2026-09-16 20:00:00+09'
FROM (VALUES
    (UUID '22c84bf2-d321-417d-b5c8-000126081002', '해운대해수욕장', 'SEA_BEACH', '126081'),
    (UUID 'e61da507-35b8-4df0-bd14-000126078002', '광안리해수욕장', 'SEA_BEACH', '126078'),
    (UUID 'a58d4557-f2b9-4dc0-b10b-000126080002', '송정해수욕장', 'SEA_BEACH', '126080'),
    (UUID 'ab454ead-b057-4d95-bc42-000126079002', '다대포해수욕장', 'SEA_BEACH', '126079'),
    (UUID 'c19e4d33-5935-4afe-bc2f-000126098002', '일광해수욕장', 'SEA_BEACH', '126098'),
    (UUID '33a80d29-34f6-4ece-bf0c-001939570002', '임랑해수욕장', 'SEA_BEACH', '1939570'),
    (UUID 'd92d54aa-e805-441a-be26-001945309002', '이기대', 'SEA_BEACH', '1945309'),
    (UUID '65d77c36-73f2-4835-b401-000126658002', '태종대', 'SEA_BEACH', '126658'),
    (UUID '333e4a51-3d27-4c66-b92e-000126088002', '오륙도 (부산 국가지질공원)', 'SEA_BEACH', '126088'),
    (UUID '22ccb1a8-8e01-46df-baab-000129602002', '청사포', 'SEA_BEACH', '129602'),
    (UUID 'd488d56a-a38c-473d-b57a-002684712002', '흰여울문화마을', 'SEA_BEACH', '2684712'),
    (UUID 'baa83839-62d0-429c-bf16-002614721002', '몰운대(부산)', 'SEA_BEACH', '2614721'),
    (UUID '21b6aacf-22f8-416f-bca0-001338947002', '삼락생태공원', 'NATURE_WALK', '1338947'),
    (UUID 'ae292166-9a62-4a7f-b7d6-000127974002', '을숙도 공원', 'NATURE_WALK', '127974'),
    (UUID '0904843d-a0a3-47de-bb5e-002661475002', '회동수원지(회동수원지 둘레길)', 'NATURE_WALK', '2661475'),
    (UUID '1a11b4e6-16ef-4bdd-b3a3-002718728002', '성지곡수원지', 'NATURE_WALK', '2718728'),
    (UUID 'b520e404-9556-49bf-b00b-002487931002', '화명생태공원', 'NATURE_WALK', '2487931'),
    (UUID '73f4f542-31ce-49ba-bd44-000126848002', '해동용궁사', 'CULTURE_TEMPLE', '126848')
) AS seed(place_feature_id, name_ko, category, source_id)
JOIN place p
  ON LOWER(REPLACE(p.name_ko, ' ', '')) = LOWER(REPLACE(seed.name_ko, ' ', ''))
WHERE NOT EXISTS (
    SELECT 1
    FROM place_feature pf
    WHERE pf.place_id = p.place_id
      AND pf.feature_type = 'CATEGORY_TAG'
      AND pf.feature_key = seed.category
)
ON CONFLICT (place_feature_id) DO NOTHING;

-- ── 3. 사진 ────────────────────────────────────────────────────────────────
--
-- 저작권 유형이 Type1(공공누리 제1유형, 자유 이용)인 것만 넣는다 —
-- V20260915030000 이 세운 기준과 같다. 🔴 해동용궁사는 Type3(제3자 저작물)라
-- **일부러 뺐다.** 아래 17장은 전부 Type1 이다.
--
-- 이미 사진이 있는 장소는 덮지 않는다.
UPDATE place
   SET photo_url = seed.photo_url,
       photo_source = '한국관광공사 공공누리 제1유형'
  FROM (VALUES
    ('해운대해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/47/4105447_image2_1.jpg'),
    ('광안리해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/45/3311245_image2_1.jpg'),
    ('송정해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/22/3495922_image2_1.jpg'),
    ('다대포해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/15/3497115_image2_1.jpg'),
    ('일광해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/94/3495494_image2_1.jpg'),
    ('임랑해수욕장', 'https://tong.visitkorea.or.kr/cms/resource/79/3495479_image2_1.jpg'),
    ('이기대', 'https://tong.visitkorea.or.kr/cms/resource/11/4096311_image2_1.jpg'),
    ('태종대', 'https://tong.visitkorea.or.kr/cms/resource/83/3506383_image2_1.jpg'),
    ('오륙도 (부산 국가지질공원)', 'https://tong.visitkorea.or.kr/cms/resource/20/3496820_image2_1.jpg'),
    ('청사포', 'https://tong.visitkorea.or.kr/cms/resource/30/3495930_image2_1.jpg'),
    ('흰여울문화마을', 'https://tong.visitkorea.or.kr/cms/resource/74/3495874_image2_1.jpg'),
    ('몰운대(부산)', 'https://tong.visitkorea.or.kr/cms/resource/30/3506330_image2_1.jpg'),
    ('삼락생태공원', 'https://tong.visitkorea.or.kr/cms/resource/90/3497090_image2_1.jpg'),
    ('을숙도 공원', 'https://tong.visitkorea.or.kr/cms/resource/21/3497121_image2_1.jpg'),
    ('회동수원지(회동수원지 둘레길)', 'https://tong.visitkorea.or.kr/cms/resource/56/3552456_image2_1.jpg'),
    ('성지곡수원지', 'https://tong.visitkorea.or.kr/cms/resource/64/3496964_image2_1.jpg'),
    ('화명생태공원', 'https://tong.visitkorea.or.kr/cms/resource/60/3497060_image2_1.jpg')
) AS seed(name_ko, photo_url)
 WHERE LOWER(REPLACE(place.name_ko, ' ', '')) = LOWER(REPLACE(seed.name_ko, ' ', ''))
   AND place.photo_url IS NULL;
