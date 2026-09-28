-- S15P21E201-1745 — 부산 주요 명소 26곳을 place 정본에 넣는다. V20260916210000(명소 넷)과 같은 방식이다.
--
-- 2026-09-26 운영에서 대표 명소 55곳을 이름으로 대조하니 26곳이 정본에 없었다 — 부산역을 치면 호텔만,
-- BIFF광장·부산타워·더베이101은 0건. 택시 카드는 카카오 검색을 합쳐 덮었지만(S15P21E201-1742)
-- 「꼭 가고 싶은 곳」은 우리 place_id 만 받고 USER_SUBMITTED 는 추천 후보에서 빠지므로 정본이 있어야 한다.
--
-- 주소·좌표·source_id 는 같은 날 운영의 KAKAO_LOCAL 출발지 검색(/api/v1/origins) 응답 그대로다. 결과마다
-- 사람이 보고 명소 그 자체인 것만 골랐다(주차장·지점·가게는 버렸다). 영업시간·평점·안전 정보는 응답에 없어
-- 만들지 않는다. 영문 이름은 공식 표기가 확실한 것만 넣고 나머지는 비운다.
--
-- 이미 있는 것은 만들지 않는다 — 공백을 무시한 같은 이름이나 같은 카카오 id 가 있으면 건너뛴다.
-- 🔴 송도해수욕장·광안대교·기장시장·금정산성은 넣지 않았다. 「부산 송도해수욕장」「부산광안대교」처럼 이름이
--    조금 다른 정본이 이미 있고, 공백을 무시하는 검색(같은 MR)이면 찾힌다.
INSERT INTO place (
    place_id, name_ko, name_en, category, address, lat, lng, created_at,
    source_type, source_id, collected_at, observed_at, dataset_version
)
SELECT
    seed.place_id, seed.name_ko, seed.name_en, seed.category, seed.address,
    seed.lat, seed.lng, seed.collected_at, seed.source_type, seed.source_id,
    seed.collected_at, NULL, seed.dataset_version
FROM (VALUES
    (UUID 'f6314671-6693-44f1-a451-693e2b28384d', '부산역', 'Busan Station', 'CITY', '부산 동구 중앙대로 206', 35.11520340622514, 129.04154985192403, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '8329752', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '38f3001e-9e2a-4010-bfe9-53d975322410', '부산타워', 'Busan Tower', 'CITY', '부산 중구 용두산길 37-30', 35.10121449509689, 129.03232871480225, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '1534486834', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'bfa0b970-f9fc-4b65-880e-8697bda93219', 'BIFF광장', 'BIFF Square', 'CITY', '부산 중구 남포동3가 15-1', 35.0988678741728, 129.029045595229, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '9650856', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '755d0f09-6bc8-4bdc-b24f-c3807856ad39', '더베이101', 'The Bay 101', 'CITY', '부산 해운대구 동백로 52', 35.1565648156251, 129.152021092751, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '27457865', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'e1aa41f8-9806-4828-8ee7-d6adb363d60b', '부산시민공원', 'Busan Citizens Park', 'NATURE_WALK', '부산 부산진구 시민공원로 73', 35.1683562959139, 129.057403818219, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '11585715', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'f092687c-b613-4fcd-9310-b9ec7e2dc55b', '동백섬', 'Dongbaekseom Island', 'NATURE_WALK', '부산 해운대구 우동 708-3', 35.1539431141198, 129.152289970167, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '8257954', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '582f4934-f07b-48a0-87bd-1ebadf61986d', '누리마루 APEC하우스', 'Nurimaru APEC House', 'CULTURE_TEMPLE', '부산 해운대구 동백로 116', 35.1523345777828, 129.151325936955, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '8192337', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '7fceb398-85d3-4379-82e9-24f9d168f24b', '송도해상케이블카', 'Songdo Marine Cable Car', 'CITY', '부산 서구 송도해변로 171', 35.076643702635, 129.02339870312, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '1878852238', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '9b3d65a0-11dc-49ee-b2de-04265e54b4d9', '이기대도시자연공원', 'Igidae Park', 'NATURE_WALK', '부산 남구 용호동 산 125-2', 35.11658442953258, 129.12255030176132, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '27327056', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '16b5983e-b870-407f-9f1a-8727b99f64fe', '영도대교', 'Yeongdo Bridge', 'CITY', '부산 영도구 대교동1가 190-1', 35.0955088868829, 129.0364786611536, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '8008973', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'e117886d-731e-44e3-a5d1-254f1e5a0567', '부산현대미술관', 'Museum of Contemporary Art Busan', 'CULTURE_TEMPLE', '부산 사하구 낙동남로 1191', 35.1092509744478, 128.942723339212, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '1257896804', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '81385a4c-6c72-4736-a077-64302f95e6f7', '부산박물관', 'Busan Museum', 'CULTURE_TEMPLE', '부산 남구 유엔평화로 63', 35.12955533767398, 129.09294128349777, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '9037691', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'a40f3852-2bf8-4e1d-bbf4-a0496fac298d', '재한유엔기념공원', 'UN Memorial Cemetery in Korea', 'CULTURE_TEMPLE', '부산 남구 유엔평화로 93', 35.127669830860576, 129.09764693985352, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '11181019', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'd8836d09-0c5c-4d15-a962-95894af98d2e', '영화의전당', 'Busan Cinema Center', 'CULTURE_TEMPLE', '부산 해운대구 수영강변대로 120', 35.1710249248016, 129.127011337686, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '23210864', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'aabf6efa-a33d-4fcc-80fc-f36742c6b2aa', '해리단길', NULL, 'CITY', '부산 해운대구 우동 517-14', 35.1651110146752, 129.157908776659, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '2101481584', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'eff3bcd3-2fa3-452e-8af4-5fe175e85107', '전포카페거리', 'Jeonpo Cafe Street', 'CAFE_HEALING', '부산 부산진구 전포대로209번길 26', 35.1554466988384, 129.062569363766, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '25661344', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'f915d71b-7013-49c3-bb32-b03f7801668f', '초량이바구길', 'Choryang Ibagu-gil', 'CULTURE_TEMPLE', '부산 동구 초량동 994-12', 35.116820678714, 129.03669648381614, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '24544632', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '73961074-6002-4dc8-81e1-44a9812688e4', '168계단', '168 Stairs', 'CULTURE_TEMPLE', '부산 동구 초량동 994-552', 35.1171489446575, 129.035395123071, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '21546007', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '3b0b739b-efc9-435c-b037-463456c5851e', '씨라이프 부산아쿠아리움', 'SEA LIFE Busan Aquarium', 'CITY', '부산 해운대구 해운대해변로 266', 35.159354377968754, 129.16099918602094, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '7906241', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'c0b7e284-3da6-446d-ac4f-a43f41dd7b43', '롯데월드 어드벤처 부산', 'Lotte World Adventure Busan', 'CITY', '부산 기장군 기장읍 동부산관광로 42', 35.19604352390804, 129.2132067508371, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '401380860', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'ba2328a3-4dfb-43be-a214-4be528670f71', '스카이라인 루지 부산', 'Skyline Luge Busan', 'CITY', '부산 기장군 기장읍 기장해안로 205', 35.19421507890638, 129.21960754383272, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '404134034', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '9ba881f5-cbee-4737-b7d0-3ad86cd4bf10', '국립해양박물관', 'National Maritime Museum of Korea', 'CULTURE_TEMPLE', '부산 영도구 해양로301번길 45', 35.078517720104706, 129.08028722464732, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '17657287', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '8c5abfe8-7454-4fcb-ad64-6ab137693b57', '암남공원', 'Amnam Park', 'NATURE_WALK', '부산 서구 암남동 산 193-4', 35.0585918147655, 129.015681238627, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '8497179', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '0e8c7d87-d793-44c9-b685-7b52052cfd5a', 'F1963', 'F1963', 'CULTURE_TEMPLE', '부산 수영구 구락로123번길 20', 35.1769280619667, 129.114938505887, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '1440701601', 'KAKAO_LOCAL_2026-09-26'),
    (UUID 'dbc84e6f-8692-4f54-b454-bc34a6d17a97', '부산어린이대공원', 'Busan Children''s Grand Park', 'NATURE_WALK', '부산 부산진구 새싹로 295', 35.1849136186692, 129.041575693763, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '12572854', 'KAKAO_LOCAL_2026-09-26'),
    (UUID '5d371063-b3fa-4ab3-8c4d-12c99ac857d6', '임시수도기념관', 'Provisional Capital Memorial Hall', 'CULTURE_TEMPLE', '부산 서구 임시수도기념로 45', 35.1035251130919, 129.01738814237, TIMESTAMPTZ '2026-09-26 17:35:00+09', 'KAKAO_LOCAL', '27019549', 'KAKAO_LOCAL_2026-09-26')
) AS seed(place_id, name_ko, name_en, category, address, lat, lng, collected_at, source_type, source_id, dataset_version)
WHERE NOT EXISTS (
    SELECT 1
    FROM place existing
    WHERE LOWER(REPLACE(existing.name_ko, ' ', '')) = LOWER(REPLACE(seed.name_ko, ' ', ''))
       OR (existing.source_type = seed.source_type AND existing.source_id = seed.source_id)
)
ON CONFLICT (place_id) DO NOTHING;

-- 🔴 사용자가 이미 기록에 이 카카오 장소를 붙였다면 USER_SUBMITTED 행이 먼저 있다(UserSubmittedPlaceService).
--    그러면 위 INSERT 는 건너뛰고, 그 행은 검색·추천에서 빠진 채 남는다. 위 26곳은 사람이 확인한 명소이므로
--    같은 카카오 id 인 행만 정본으로 올린다.
UPDATE place
SET curation_status = 'CURATED'
WHERE source_type = 'KAKAO_LOCAL'
  AND source_id IN ('8329752', '1534486834', '9650856', '27457865', '11585715', '8257954', '8192337', '1878852238', '27327056', '8008973', '1257896804', '9037691', '11181019', '23210864', '2101481584', '25661344', '24544632', '21546007', '7906241', '401380860', '404134034', '17657287', '8497179', '1440701601', '12572854', '27019549')
  AND curation_status = 'USER_SUBMITTED';
