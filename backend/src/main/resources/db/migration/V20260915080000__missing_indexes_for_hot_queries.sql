-- S15P21E201-1011 — 자주 도는 조회 셋에 색인이 없다.
--
-- 지금은 데이터가 적어 안 느리다. 늘면 한꺼번에 느려진다. 지금 넣는 것이 싸다.
--
-- 🔴 왜 CREATE INDEX CONCURRENTLY 가 아닌가
--    CONCURRENTLY 는 쓰기를 안 막는 대신 트랜잭션 안에서 못 돈다. Flyway 는 마이그레이션을
--    트랜잭션으로 감싸므로 그대로 쓰면 기동이 실패한다. 그리고 이 세 표는 지금 작다
--    (place 약 2,700행). 평범한 CREATE INDEX 가 밀리초 단위로 끝나므로 잠금이 문제가 되지
--    않는다. 표가 커진 뒤에 색인을 더할 일이 생기면 그때는 이 선택을 다시 해야 한다.
--
-- 🔴 왜 IF NOT EXISTS 인가
--    2026-09-15 오늘 하루에만 여러 갈래가 동시에 back/dev 로 들어오고 있다. 다른 작업이
--    같은 이름의 색인을 먼저 만들면 이 파일은 실패하고, Flyway 실패는 테스트가 아니라
--    서버 기동을 멈춘다 — 오늘 오전 운영 502 (S15P21E201-966)가 정확히 그 종류였다.
--    색인 생성은 "이미 있으면 그냥 둔다" 가 안전한 몇 안 되는 연산이라 여기서는 그렇게 한다.

-- ── 1. 좌표 경계상자 (추천의 후보 조회) ──────────────────────────────────────
-- PlaceRepository.findWithinBoundingBox / findHavingFeature 가 이 모양으로 훑는다:
--   WHERE lat IS NOT NULL AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?
-- 색인이 없어서 장소가 늘수록 전 테이블 스캔이 그대로 늘어난다. 추천이 부를 때마다 돈다.
--
-- 부분 색인(WHERE lat IS NOT NULL)인 이유: 질의가 그 조건을 그대로 달고 있고,
-- ck_place_origin_pair 때문에 lat 과 lng 는 함께 있거나 함께 없다. 좌표 없는 장소를
-- 색인에서 빼면 색인이 작아진다.
--
-- 🔴 이것은 진짜 2차원 색인이 아니다. 앞 칸(lat)으로 범위를 좁히고 lng 는 그 안에서
--    거르는 것이라, 위아래로 긴 상자에서는 덜 효과적이다. 제대로 하려면 PostGIS 같은
--    공간 색인이 필요한데 그것은 이 티켓의 범위가 아니다. 지금 문제는 "색인이 아예 없어서
--    전부 훑는 것" 이고, 그건 이걸로 사라진다.
CREATE INDEX IF NOT EXISTS ix_place_coords
    ON place (lat, lng)
    WHERE lat IS NOT NULL;

-- ── 2. 여행별 기록 조회 ──────────────────────────────────────────────────────
-- StoryRepository.findTripStories 가 이 모양이다:
--   WHERE trip_id = ? AND deleted_at IS NULL AND moderation_state = 'VISIBLE'
--   ORDER BY created_at ASC, story_id ASC
-- 거르는 칸과 정렬 칸을 그대로 담아 정렬까지 색인이 해 준다.
-- 부분 조건은 바로 옆 ix_story_feed 가 이미 쓰는 것과 같은 것이다 — 안 보이는 기록을
-- 색인에서 빼면 색인이 작아지고, 어차피 이 질의는 그 둘을 항상 건다.
CREATE INDEX IF NOT EXISTS ix_story_trip
    ON story (trip_id, created_at, story_id)
    WHERE deleted_at IS NULL AND moderation_state = 'VISIBLE';

-- 🔴 story(place_id) 는 일부러 안 만든다.
--    티켓(-1011)은 "여행별·장소별 기록 조회" 를 같이 적었지만, place_id 로 거르는 질의가
--    이 저장소에 아직 하나도 없다 (장소별 기록 수는 -992 이고 그 앞에 -971 이 있다).
--    색인은 읽기를 빠르게 하는 대신 행을 넣고 고칠 때마다 함께 갱신된다 — 읽는 질의가
--    없으면 비용만 내고 이득이 0 이다. 그 기능이 실제로 들어올 때 같이 만든다.

-- ── 3. 토큰 발급 (auth_identity) ────────────────────────────────────────────
-- AuthIdentityRepository.findAllByUserUserId 가 user_id 로 찾는다.
--
-- 🔴 fk_auth_identity_user 외래키가 있는데도 색인이 없다. PostgreSQL 은 외래키를 만들 때
--    참조당하는 쪽(app_user 의 기본키)만 쓰고 참조하는 쪽에는 색인을 만들어 주지 않는다.
--    "제약이 있으니 빠르겠지" 로 넘어가기 쉬운 자리다.
CREATE INDEX IF NOT EXISTS ix_auth_identity_user
    ON auth_identity (user_id);
