-- S15P21E201-317 — 비회원이 만든 여행으로 일정을 만들고 고칠 수 있게 한다.
--
-- V20260910040000 이 여행·구성원·두 스냅샷의 app_user 외래키를 뗐지만, 그 여행에서 일정을 만들고
-- 고치는 자리의 셋은 남아 있었다. 셋 다 요청자 ID 를 그대로 적는데 익명 세션 ID 는 app_user 에
-- 없어서, 비회원 여행의 첫 일정 저장이 외래키 위반으로 롤백되고 작업이 FAILED 로 끝났다.
--
--   itinerary_versions.created_by              — 일정 생성·코스 선택·모든 편집이 판을 하나씩 쌓는다
--   itinerary_excluded_place.excluded_by       — 일정에서 장소를 빼면 적힌다
--   recommendation_place_action.decided_by_user_id — 추천 후보를 담거나 뺀 사람
--
-- 앞의 마이그레이션과 같은 이유로 대신 걸 DB 제약이 없다 — PostgreSQL CHECK 는 다른 표를 볼 수 없다.
-- 값이 회원이거나 실제 익명 세션이라는 것은 인증이 보장한다(JWT 필터와 익명 세션 필터만 신원을 채운다).
-- 로그인하면 승계가 이 칸들을 새 계정 ID 로 바꾼다(JpaTripRepository#claimAnonymousTrips).
--
-- 계정 탈퇴는 app_user 행을 지우지 않고 비우므로(AccountDeletionService) 이 외래키가 막던 삭제는 원래 없다.
ALTER TABLE itinerary_versions DROP CONSTRAINT fk_itinerary_version_created_by;
ALTER TABLE itinerary_excluded_place DROP CONSTRAINT fk_itinerary_excluded_by;
ALTER TABLE recommendation_place_action DROP CONSTRAINT fk_recommendation_place_action_decided_by;
