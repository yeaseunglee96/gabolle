-- S15P21E201-1618 — 새 갈래 FESTIVAL_EVENT 「축제·행사」, 그리고 갈래가 빈 장소 채우기.
--
-- 🔴 왜 FESTIVAL 이 아닌가
--    FESTIVAL 은 이미 탐색(둘러보기) 사전(INTEREST_TAG)의 「축제」다. 온보딩 갈래 사전(CATEGORY_TAG)과 탐색 사전은
--    낱말이 겹치지 않게 지킨다(PlaceFeatureCodeMapTest) — 겹치면 같은 낱말이 쓴 곳에 따라 두 뜻이 되어 데이터만
--    봐서는 구별할 수 없다. 탐색의 NATURE 와 온보딩의 NATURE_WALK 가 갈린 것과 같은 이유다.
--
-- 🔴 왜
--    관광공사 축제(유형 15)는 앱의 여섯 갈래에 맞는 것이 없어 갈래가 비어 있었다(2026-09-25 운영 62곳).
--    추천 후보 조회는 갈래가 빈 장소를 늘 빼므로(PlaceCandidateQueryService — NON_EMPTY_CATEGORY),
--    같은 기간·같은 지역에 광안리 드론 라이트쇼·달밤에체조가 있어도 추천 일정에 안 들어갔다.
--    사용자 결정: 새 갈래를 만든다. 여행 날짜에 여는 축제만 나오는 것은 코드가 지킨다(일정 조립·추천 엔진).
--
-- 운영의 빈 갈래 87곳(2026-09-25 읽기) — 무엇을 채우고 무엇을 두나
--    · 축제 62곳 — 전부 축제 기간표(place_event_period)가 있다 → FESTIVAL_EVENT
--    · 카카오 숙소 2곳(파라다이스호텔부산·신라스테이 해운대) → LODGING. 원천(카카오)의 분류가 숙박이다
--    · 레포츠 22곳(캠핑·서핑학교·골프·사격) → 그대로 비운다(사용자 결정). TourApiCategory 가 일부러 비운
--      것이다 — 추천에는 안 나오고 검색·꼭 갈 곳으로만 쓴다
--    · 「시험 숙소(해운대)」(출처 KAKAO, gabolle-test-lodging) — 운영에 들어간 시험용 행이다. 이 파일은 안
--      건드린다. iOS 심사 뒤 사람이 지운다(절차는 MR 에 적었다)

-- 1. 사전에 낱말을 더한다 — 취향 답(CATEGORY)이 FESTIVAL_EVENT 를 받으려면 여기 있어야 한다
--    (ck_preference_answer_category_dictionary). 장소 표식도 외래키로 이 표를 가리킨다.
INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note)
VALUES ('CATEGORY_TAG', 'FESTIVAL_EVENT', '축제·행사', 'S15P21E201-1618 — 관광공사 축제(유형 15). 여행 날짜에 여는 것만 추천된다')
ON CONFLICT DO NOTHING;

-- 2. 축제 — 기간표가 있는 빈 갈래 장소. 갈래가 이미 있는 곳은 안 건드린다(모름 → 앎 한 방향).
UPDATE place p
   SET category = 'FESTIVAL_EVENT'
 WHERE (p.category IS NULL OR btrim(p.category) = '')
   AND EXISTS (SELECT 1 FROM place_event_period e WHERE e.place_id = p.place_id);

-- 3. 카카오 숙소 둘
UPDATE place
   SET category = 'LODGING'
 WHERE source_type = 'KAKAO_LOCAL'
   AND source_id IN ('8625845', '1295735325')
   AND (category IS NULL OR btrim(category) = '');

-- 4. 갈래 표식 — 적재기가 갈래를 채울 때 같이 넣는 것과 같은 모양(태그 값 true, 원천 분류에서 옮겨 ESTIMATED).
--    이미 있으면 안 넣는다.
INSERT INTO place_feature (
    place_feature_id, place_id, feature_type, feature_key, value,
    evidence_status, source_type, source_id, observed_at, source_version, created_at
)
SELECT gen_random_uuid(), p.place_id, 'CATEGORY_TAG', p.category,
       'true'::jsonb,
       'ESTIMATED', p.source_type, p.source_id, NULL,
       'festival-category-20260925', now()
  FROM place p
 WHERE (p.category = 'FESTIVAL_EVENT'
        OR (p.category = 'LODGING' AND p.source_type = 'KAKAO_LOCAL' AND p.source_id IN ('8625845', '1295735325')))
   AND NOT EXISTS (
        SELECT 1 FROM place_feature f
         WHERE f.place_id = p.place_id
           AND f.feature_type = 'CATEGORY_TAG'
           AND f.feature_key = p.category)
ON CONFLICT DO NOTHING;
