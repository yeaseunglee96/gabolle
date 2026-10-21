-- S15P21E201-1857 — 관광공사 무슬림 친화 식당 목록 중 정본에 없던 부산 14곳을 넣는다.
--
-- 출처는 한국관광공사 「Muslim-Friendly Restaurants in Korea」(정보 기준 2021-12, 2022-11 발행)의 부산 28곳이다.
-- 2026-09-29 운영 place 와 좌표 150m·이름으로 대조했더니 11곳은 이미 있었고 17곳이 없었다.
-- 17곳은 웹 검색과 카카오맵 화면으로 지금도 영업하는지 봤다(2026-09-30). 전화가 같고 영업시간이 올라와 있거나
-- 2026년 후기가 있는 곳만 넣었다. 뺀 셋은 이렇다.
--
--   106 남천숯불장어구이 — 지도에 2026-08-02 ~ 09-30 휴무 공지만 있고 다시 연다는 근거가 없다
--   110 어부(해운대 조개구이) — 지도에서 같은 가게를 찾지 못했다
--   246 우리횟집(자갈치시장) — 영업시간이 없고 마지막 후기가 2024-02 다
--
-- 할랄·포크프리 등급은 넣지 않는다. 목록이 2021년 기준이고 관광공사가 2022년에 분류 사업을 접어서, 그 등급을 지금의
-- 사실로 적을 근거가 없다. DIETARY_SUPPORT_TAG 는 ESTIMATED 저장을 DB 가 막기도 한다(V20260907003000).
-- 여기서는 장소 행만 넣고, 복국집 하나에만 SBIZ 적재기가 붙였을 음식 태그를 같이 붙인다.
--
-- 좌표는 목록에 적힌 값을 쓴다. 지도의 현재 위치와 60m 안으로 맞는 곳이 열 곳이다. 넷은 고쳤다 — 095 는 가게를 옮겼고,
-- 099 는 목록 좌표가 해운대점 것을 그대로 적어 8.7km 어긋나 있고, 102·107 은 건물·번지가 달라 60m 를 넘었다.
-- 이 넷만 카카오맵 화면의 위치를 경위도로 옮겨 적었다. 주소는 영업 확인 때 본 현재 도로명 주소다.
--
-- 이미 있는 것은 만들지 않는다. 150m 안에 가게 이름의 핵심 낱말(한글·영문)이 든 장소가 있으면 건너뛴다 —
-- 운영에는 같은 가게가 한글 이름 행과 영문 이름 행으로 따로 있는 경우가 있어서(펀자브·헬로인디아), 이름 완전일치로는
-- 못 막는다. 사진은 넣지 않았다. 라이선스가 확인되는 사진(공공누리·커먼즈)을 찾지 못했다.
INSERT INTO place (
    place_id, name_ko, name_en, category, address, lat, lng, created_at,
    source_type, source_id, collected_at, observed_at, dataset_version
)
SELECT
    seed.place_id, seed.name_ko, seed.name_en, 'FOOD', seed.address,
    seed.lat, seed.lng, TIMESTAMPTZ '2026-09-30 02:00:00+09',
    'KTO_MUSLIM_FRIENDLY', seed.source_id, TIMESTAMPTZ '2026-09-30 02:00:00+09',
    NULL, 'kto-muslim-friendly-2021-12'
FROM (VALUES
    (UUID '2e56ae4a-c9be-56ce-9f22-224f63445fb7', '095', '라마앤바바나 서면점', 'Rama & Bavana (Seomyeon Branch)', '부산광역시 부산진구 중앙대로680번길 45-8', 35.1537211, 129.0618762, '라마앤바바나', 'Rama'),
    (UUID '80f22b36-4381-5415-88fe-b0a64a914f76', '096', '원조할매복국', 'Wonjo Halmae Bokguk', '부산광역시 해운대구 달맞이길62번길 1', 35.160954, 129.171545, '할매복국', 'Halmae Bokguk'),
    (UUID 'c5c8e28c-05c1-5765-a466-75c9528819cf', '099', '봄베이브로이 문현점', 'Bombay Brau (Munhyeon Branch)', '부산광역시 남구 문현금융로 40', 35.1463313, 129.0661222, '봄베이', 'Bombay'),
    (UUID '2dc52779-0290-562e-9bf1-b6c664544373', '100', '뉴리틀인디아 부산역점', 'New Little India (Busan Station Branch)', '부산광역시 동구 중앙대로 205', 35.115711, 129.039587, '리틀인디아', 'Little India'),
    (UUID 'f44d0dcb-b82c-504a-ab97-c6ded166acd3', '102', '무궁화 롯데호텔 부산', 'Mugunghwa (Lotte Hotel Busan)', '부산광역시 부산진구 가야대로 772', 35.1565585, 129.0556933, '무궁화', 'Mugunghwa'),
    (UUID '09291a8c-b571-5e77-89e3-b7d94efadf09', '107', '봄베이브로이 광복점', 'Bombay Brau', '부산광역시 중구 광복로 83', 35.0986627, 129.0339748, '봄베이', 'Bombay'),
    (UUID 'b897f72b-65a2-5bf9-b139-d6d067143cea', '109', '봄베이브로이 해운대점', 'Bombay Brau (Haeundae Branch)', '부산광역시 해운대구 구남로 30', 35.161482, 129.160453, '봄베이', 'Bombay'),
    (UUID 'f51240bb-615b-554d-afa1-84a1a0db5299', '111', '용장어요리전문점', 'Yongjangeo Restaurant', '부산광역시 강서구 낙동남로682번길 94', 35.1181886, 128.8919971, '용장어', 'Yongjangeo'),
    (UUID '9088c5b5-0054-50a6-aec1-6ed0c245d69f', '289', '행복을짓는 수향촌밥상', 'Suhyang Babsang Builds Happiness', '부산광역시 사하구 다대로 592', 35.053567, 128.970582, '수향촌', 'Suhyang'),
    (UUID '2ad4abed-b5df-54ad-8a02-06d2e7d24db1', '322', '흙시루', 'Hurgsiru', '부산광역시 기장군 기장읍 차성로451번길 28', 35.257068, 129.216415, '흙시루', 'Hurgsiru'),
    (UUID '8bbef966-950c-5baf-b026-c6ef669e4f44', '323', '원조서울삼계탕', 'Seoul Samgyetang', '부산광역시 중구 남포길 36', 35.0982708, 129.0326242, '서울삼계탕', 'Seoul Samgyetang'),
    (UUID '63048e53-e87b-547c-aa83-44040094627b', '324', '서가네오리', 'Seogane Ori', '부산광역시 부산진구 가야공원로 83-33', 35.145081, 129.028805, '서가네', 'Seogane'),
    (UUID '09e507f5-2c10-50e7-8523-5043bd8d8322', '325', '산골애', 'Sangole', '부산광역시 강서구 명지오션시티1로 173', 35.079767, 128.903796, '산골애', 'Sangole'),
    (UUID 'c0b52df4-1cb4-56d9-93fa-bb457bf606da', '326', '장수삼', 'Jangsusam', '부산광역시 수영구 수영성로3번길 7', 35.169521, 129.119584, '장수삼', 'Jangsusam')
) AS seed(place_id, source_id, name_ko, name_en, address, lat, lng, token_ko, token_en)
WHERE NOT EXISTS (
    SELECT 1
    FROM place existing
    WHERE (existing.source_type = 'KTO_MUSLIM_FRIENDLY' AND existing.source_id = seed.source_id)
       OR (abs(existing.lat - seed.lat) < 0.00135
           AND abs(existing.lng - seed.lng) < 0.00165
           AND (REPLACE(existing.name_ko, ' ', '') LIKE '%' || seed.token_ko || '%'
                OR existing.name_en ILIKE '%' || seed.token_en || '%'
                OR existing.name_ko ILIKE '%' || seed.token_en || '%'))
)
ON CONFLICT (place_id) DO NOTHING;

-- 사용자가 기록에 먼저 붙여 USER_SUBMITTED 로 들어온 같은 가게가 있으면 위 INSERT 는 건너뛰고, 그 행은 검색·추천에서
-- 빠진 채 남는다(V20260926180000 과 같은 사정). 같은 조건으로 찾은 그 행을 정본으로 올린다.
UPDATE place p
SET curation_status = 'CURATED'
FROM (VALUES
    (35.1537211, 129.0618762, '라마앤바바나', 'Rama'),
    (35.160954, 129.171545, '할매복국', 'Halmae Bokguk'),
    (35.1463313, 129.0661222, '봄베이', 'Bombay'),
    (35.115711, 129.039587, '리틀인디아', 'Little India'),
    (35.1565585, 129.0556933, '무궁화', 'Mugunghwa'),
    (35.0986627, 129.0339748, '봄베이', 'Bombay'),
    (35.161482, 129.160453, '봄베이', 'Bombay'),
    (35.1181886, 128.8919971, '용장어', 'Yongjangeo'),
    (35.053567, 128.970582, '수향촌', 'Suhyang'),
    (35.257068, 129.216415, '흙시루', 'Hurgsiru'),
    (35.0982708, 129.0326242, '서울삼계탕', 'Seoul Samgyetang'),
    (35.145081, 129.028805, '서가네', 'Seogane'),
    (35.079767, 128.903796, '산골애', 'Sangole'),
    (35.169521, 129.119584, '장수삼', 'Jangsusam')
) AS seed(lat, lng, token_ko, token_en)
WHERE p.curation_status = 'USER_SUBMITTED'
  AND abs(p.lat - seed.lat) < 0.00135
  AND abs(p.lng - seed.lng) < 0.00165
  AND (REPLACE(p.name_ko, ' ', '') LIKE '%' || seed.token_ko || '%'
       OR p.name_en ILIKE '%' || seed.token_en || '%'
       OR p.name_ko ILIKE '%' || seed.token_en || '%');

-- 원조할매복국 — SBIZ 적재기라면 소분류 「복 요리 전문」에서 CUISINE_TAG SEAFOOD 를, 이름의 「복국」에서
-- DESIRED_FOOD_TAG BOKGUK 을 붙였을 가게다. 같은 모양으로 붙인다. place 와 조인하므로 빈 시험 DB 에서는 0행이다.
INSERT INTO place_feature (
    place_feature_id, place_id, feature_type, feature_key, value,
    evidence_status, source_type, source_id, observed_at, source_version, created_at
)
SELECT
    seed.place_feature_id, p.place_id, seed.feature_type, seed.feature_key, 'true'::jsonb,
    'ESTIMATED', 'KTO_MUSLIM_FRIENDLY', '096', NULL,
    'kto-muslim-friendly-2021-12', TIMESTAMPTZ '2026-09-30 02:00:00+09'
FROM (VALUES
    (UUID '27f25a98-5212-5dae-aad6-144e2ef85ccb', 'CUISINE_TAG', 'SEAFOOD'),
    (UUID 'e2f9b6bc-e0dc-58a7-b7ed-40806481beab', 'DESIRED_FOOD_TAG', 'BOKGUK')
) AS seed(place_feature_id, feature_type, feature_key)
JOIN place p ON p.place_id = UUID '80f22b36-4381-5415-88fe-b0a64a914f76'
WHERE NOT EXISTS (
    SELECT 1 FROM place_feature f
    WHERE f.place_id = p.place_id
      AND f.feature_type = seed.feature_type
      AND f.feature_key = seed.feature_key
);

-- 되돌리기
--   DELETE FROM place_feature WHERE source_version = 'kto-muslim-friendly-2021-12';
--   DELETE FROM place WHERE dataset_version = 'kto-muslim-friendly-2021-12';
