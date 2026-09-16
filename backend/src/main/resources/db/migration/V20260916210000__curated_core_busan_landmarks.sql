-- S15P21E201-76 — 앱의 대표 카드와 장소 검색 예시에 쓰는 부산 핵심 명소 네 곳을
-- 실제 place 정본에 넣는다. 2026-09-15 운영 API 실측에서 정확 이름이 모두 빠져 있었고,
-- 감천문화마을 검색은 감천사·횟집만 돌려줬다. 화면에만 가짜 ID를 만들면 일정 생성의
-- trip_seed_place FK가 실패하므로 정식 place_id가 필요하다.
--
-- 주소·좌표·source_id는 같은 날 운영의 KAKAO_LOCAL 출발지 검색 응답에서 가져왔다.
-- 영업시간·평점·안전 정보는 그 응답에 없으므로 만들지 않는다. 같은 이름이 이후 다른
-- 적재에서 먼저 들어온 환경에서는 중복 장소를 만들지 않고 기존 정본을 존중한다.
INSERT INTO place (
    place_id, name_ko, name_en, category, address, lat, lng, created_at,
    source_type, source_id, collected_at, observed_at, dataset_version
)
SELECT
    seed.place_id, seed.name_ko, seed.name_en, seed.category, seed.address,
    seed.lat, seed.lng, seed.collected_at, seed.source_type, seed.source_id,
    seed.collected_at, NULL, seed.dataset_version
FROM (VALUES
    (UUID '87a75bd0-579d-4c80-a44b-021362956001', '감천문화마을', 'Gamcheon Culture Village', 'CULTURE_TEMPLE', '부산 사하구 감천동 1-17', 35.09740872250286, 129.01056080474402, TIMESTAMPTZ '2026-09-15 12:10:00+09', 'KAKAO_LOCAL', '21362956', 'KAKAO_LOCAL_2026-09-15'),
    (UUID '434a90ee-c94f-4f0e-a709-007913306001', '해운대해수욕장', 'Haeundae Beach', 'SEA_BEACH', '부산 해운대구 우동', 35.1585232170784, 129.159854668484, TIMESTAMPTZ '2026-09-15 12:10:00+09', 'KAKAO_LOCAL', '7913306', 'KAKAO_LOCAL_2026-09-15'),
    (UUID '9110a719-00ba-4ee7-9c53-026884008001', '범어사', 'Beomeosa Temple', 'CULTURE_TEMPLE', '부산 금정구 범어사로 250', 35.2835189605105, 129.068459786947, TIMESTAMPTZ '2026-09-15 12:10:00+09', 'KAKAO_LOCAL', '26884008', 'KAKAO_LOCAL_2026-09-15'),
    (UUID '76363fd8-a555-456e-896a-008202423001', '광안리해수욕장', 'Gwangalli Beach', 'SEA_BEACH', '부산 수영구 광안해변로 219', 35.1531932736837, 129.118976093583, TIMESTAMPTZ '2026-09-15 12:10:00+09', 'KAKAO_LOCAL', '8202423', 'KAKAO_LOCAL_2026-09-15')
) AS seed(place_id, name_ko, name_en, category, address, lat, lng, collected_at, source_type, source_id, dataset_version)
WHERE NOT EXISTS (
    SELECT 1
    FROM place existing
    WHERE LOWER(REPLACE(existing.name_ko, ' ', '')) = LOWER(REPLACE(seed.name_ko, ' ', ''))
       OR (existing.source_type = seed.source_type AND existing.source_id = seed.source_id)
)
ON CONFLICT (place_id) DO NOTHING;
