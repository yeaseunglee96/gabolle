-- S15P21E201-2059 — 로컬 탐색: 「카페」 갈래(LOCAL_CAFE)와 카페·현지인 추천의 인증 낱말 셋을 사전에 더한다.
--
-- 앞 작업(S15P21E201-2051, V20261013200000)이 「맛집」 갈래를 만들었다. 이번에 같은 틀을 넓힌다.
--
--   INTEREST_TAG       / LOCAL_CAFE         카페 갈래에 든다. 맛집(LOCAL_RESTAURANT)과 똑같이 구 → 동으로 골라 본다.
--                                           구·동은 맛집이 쓰는 ADMIN_AREA_TAG 를 그대로 쓴다 — 갈래만 다르다.
--   CERTIFICATION_TAG  / LOCAL_SURVEY       부산 현지인 설문에서 추천받은 곳.      value = {"source":"현지인 추천","recommenders":n}
--   CERTIFICATION_TAG  / KTO_CAFE           한국관광공사가 소개한 카페.            value = {"source":"한국관광공사"}
--   CERTIFICATION_TAG  / REDTABLE_FEATURED  레드테이블(부산광역시 식당 목록)이 소개한 카페.
--                                           value = {"source":"레드테이블"} — 방송·수상 이력도 적힌 곳이면 "broadcastOrAward":true 가 더 붙는다
--
-- 인증 낱말 셋은 공공기관 인증(BUSAN_FOOD_INFO · MODEL_RESTAURANT · TAXI_DRIVER_PICK)과 같은 서랍(CERTIFICATION_TAG)이라
-- 카드의 출처 배지가 한 줄로 나란히 선다. 이름이 「인증」이어도 현지인 추천은 기관이 인증한 것이 아니라 사람들의 추천이다 —
-- 화면은 낱말마다 다른 이름표(「현지인 추천」 · 「관광공사 추천」 · 「레드테이블」)를 달아 출처를 사실대로 밝힌다.
--
-- 🔴 허용 목록(ck_place_feature_type · ck_place_feature_key_shape)을 고치지 않는다 — 고칠 것이 없어서다.
--    그 둘이 가르는 것은 feature_type(종류) 뿐이고 feature_key(낱말)는 안 가린다. 새 종류가 아니라 이미 허용된 종류
--    (INTEREST_TAG · CERTIFICATION_TAG)에 낱말만 더하므로, 이번 마이그레이션은 사전(place_feature_code)에 행을 넣는 일이 전부다.
--    (만약 종류를 더해야 했다면 바로 앞 정의 — ck_place_feature_type 도 ck_place_feature_key_shape 도 V20261013200000 — 의 목록을
--    그대로 복사한 뒤 새 값만 더하는 누적이어야 한다. PlaceFeatureTypeConstraintTest 가 지킨다.)
--    place.source_type · place_feature.source_type 에도 허용 목록(CHECK)이 없다 — 새 출처 값(BUSAN_LOCAL_CAFE · LOCAL_SURVEY)은
--    그냥 들어간다.
--
-- 🔴 LOCAL_CAFE 는 탐색 여덟 갈래(InterestTagCode)에도, 온보딩 취향 여섯(CATEGORY_TAG)에도 넣지 않는다.
--    CATEGORY_TAG 의 CAFE_HEALING(카페·힐링)은 사용자가 온보딩에서 고르는 취향이고, 이 낱말은 탐색 화면의 칩이다 — 이름이 비슷해도
--    다른 서랍의 다른 낱말이다. 겹치면 추천 점수가 조용히 달라지므로 PlaceFeatureCodeMapTest 가 겹침 없음을 잰다.
--    맛집과 마찬가지로 자료가 없을 때는 /facets 응답에 아예 없다가, 적재가 돌면 맛집 바로 뒤에 붙는다(ExtraInterestTagCode).
--
-- 이 마이그레이션은 장소에 아무 표식도 달지 않는다. 표식을 다는 것은 별도다 — 설문 47곳은 멱등 SQL
-- (backend/scripts/local-picks-20261015/), 카페 250곳은 적재기(LocalCafeLoaderRunner)이고, 둘 다 배포(이 사전)가 먼저 나간 뒤에 돈다.
INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES
    ('INTEREST_TAG', 'LOCAL_CAFE', '카페',
     '탐색 갈래이지만 여덟이 아니다 — 맛집(LOCAL_RESTAURANT)과 같은 틀로 구 → 동을 골라 본다. 적재가 돌기 전에는 응답에 없다 (S15P21E201-2059)'),
    ('CERTIFICATION_TAG', 'LOCAL_SURVEY', '현지인 추천',
     '부산 현지인 설문에서 추천받은 곳. 공공기관 인증이 아니라 사람들의 추천이다. 값에 추천한 사람 수(recommenders)가 든다 (S15P21E201-2059)'),
    ('CERTIFICATION_TAG', 'KTO_CAFE', '한국관광공사',
     '한국관광공사(관광정보 서비스)가 소개한 카페. 카페 적재기가 단다 (S15P21E201-2059)'),
    ('CERTIFICATION_TAG', 'REDTABLE_FEATURED', '레드테이블',
     '레드테이블(부산광역시 식당 목록)이 소개한 카페. 방송·수상 이력이 적힌 곳은 값에 broadcastOrAward:true 가 붙는다 (S15P21E201-2059)')
ON CONFLICT (feature_type, feature_key) DO NOTHING;
