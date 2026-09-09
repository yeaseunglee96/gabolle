-- S15P21E201-317 — 가입 시 익명 여행 승계.
--
-- 가입 직전까지 만든 여행이 사라지면 사용자는 가입을 손해로 느낀다. 그러려면 가입 전에도
-- 여행을 만들 수 있어야 하는데, 지금 trip.owner_user_id·trip_member.user_id·
-- preference_snapshot.user_id·constraint_snapshot.user_id 는 전부 app_user 에 FK 가 걸려
-- 있다 — 익명 세션(anonymous_session.session_id, S15P21E201-303)은 app_user 표에 없는
-- UUID 라서 그대로는 이 네 표 중 어디에도 못 들어간다.
--
-- 🔴 owner_type 은 trip 표에만 둔다. 승계 대상을 찾는 질의("이 세션이 만든 여행")가 이
-- 표 하나만 보면 되고, trip_member·preference_snapshot·constraint_snapshot 은 trip_id 로
-- 옮길 행을 이미 특정할 수 있어 자기 표에 따로 구분자를 둘 이유가 없다.
ALTER TABLE trip
    ADD COLUMN owner_type VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE trip
    ALTER COLUMN owner_type DROP DEFAULT;
ALTER TABLE trip
    ADD CONSTRAINT ck_trip_owner_type CHECK (owner_type IN ('USER', 'ANONYMOUS'));

-- 🔴 네 FK 를 뗀다 — 익명 세션 UUID 는 app_user 표에 없으니 그대로 두면 익명 여행 생성이
-- 전부 FK 위반으로 막힌다. PostgreSQL CHECK 제약은 서브쿼리(다른 표에 있는지 확인)를
-- 못 써서 "app_user 또는 anonymous_session 중 하나에는 있어야 한다" 를 DB 제약으로
-- 대신 걸 방법이 없다 — 그 검증은 응용 계층이 진다(Spring Security 인증이 이미 두 값
-- 중 하나만 통과시킨다: HmacJwtAuthenticationFilter 는 회원 UUID 를, 익명 인증 필터는
-- anonymous_session 에 실제로 있는 세션만 principal 로 채운다).
ALTER TABLE trip DROP CONSTRAINT fk_trip_owner;
ALTER TABLE trip_member DROP CONSTRAINT fk_trip_member_user;
ALTER TABLE preference_snapshot DROP CONSTRAINT fk_preference_snapshot_user;
ALTER TABLE constraint_snapshot DROP CONSTRAINT fk_constraint_snapshot_user;
