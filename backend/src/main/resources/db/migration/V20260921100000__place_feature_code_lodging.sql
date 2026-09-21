-- S15P21E201-1383 — 갈래 사전에 숙소(LODGING)를 더한다.
--
-- 숙박 65곳은 이름·주소·좌표까지 이미 들어와 있었는데 place.category 칸만 비어 있어서
-- 앱의 숙소 목록이 0곳이었다. TourApiCategory 가 관광공사 숙박 대분류(B02)를 LODGING 으로
-- 옮기도록 고쳤는데, 적재가 place.category 와 함께 CATEGORY_TAG 표식도 넣기 때문에
-- 이 사전에 LODGING 이 없으면 외래키(fk_place_feature_code)에 걸려 적재가 통째로 죽는다.
--
-- 🔴 실제로 죽었다. 시험에서 먼저 걸렸다:
--    Key (feature_type, dictionary_key)=(CATEGORY_TAG, LODGING) is not present in place_feature_code
--
-- place_feature_code 표 주석이 말하는 그대로 한다 — 「단어를 더할 때는 이 표에 행을 넣는다.
-- 스키마를 고치지 않는다.」
--
-- 🔴 LODGING 은 온보딩 취향 여섯이 아니다. 사용자가 이 낱말을 보내는 일은 없고,
--    숙소 지정만 PlaceRepository.findByCategoryIn 으로 따로 찾는다. 일반 추천 후보에서는
--    PlaceCandidateQueryService 가 이 갈래를 빼므로(NOT_ACCOMMODATION) 관광 일정에 호텔이
--    섞이지 않는다. 여섯과 겹치지 않는다는 것은 TourApiCategoryTest 가 지킨다.

INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES
    ('CATEGORY_TAG', 'LODGING', '숙소',
     '취향 여섯이 아니다 — 숙소 지정 전용. 일반 후보에서는 빠진다 (S15P21E201-1383)')
ON CONFLICT (feature_type, feature_key) DO NOTHING;
