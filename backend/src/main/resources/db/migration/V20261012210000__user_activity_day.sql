-- S15P21E201-2010 — 「활성 사용자」를 세기 위한 하루 한 줄 기록.
--
-- MAU(월간 활성 사용자 — 30일 동안 앱을 실제로 쓴 서로 다른 사람 수)를 세려면
-- 「누가 어느 날 왔나」가 있어야 하는데, 지금까지는 사용자별 마지막 활동 시각조차 없었다.
-- 그래서 로그인한 요청이 들어오면 (사용자, 한국 날짜) 한 줄을 남긴다. 같은 날 두 번째부터는 아무것도 안 쓴다.
--
-- 🔴 개인정보: IP·기기·경로·시각은 남기지 않는다. 날짜 하나뿐이다. 계정을 지우면 함께 지워진다(ON DELETE CASCADE).
CREATE TABLE user_activity_day (
    user_id     UUID        NOT NULL,
    day         DATE        NOT NULL,
    -- REQUEST = 실제 요청에서 기록. BACKFILL = 이 표가 생기기 전 기록(로그인·토큰 갱신·일정 생성 등)에서 거꾸로 채운 것.
    source      VARCHAR(10) NOT NULL DEFAULT 'REQUEST',
    CONSTRAINT pk_user_activity_day PRIMARY KEY (user_id, day),
    CONSTRAINT fk_user_activity_day_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_activity_day_source CHECK (source IN ('REQUEST', 'BACKFILL'))
);
CREATE INDEX ix_user_activity_day_day ON user_activity_day (day);

-- 로그인 안 한 사람(익명 출입증). 출입증 번호는 무작위 값이라 사람을 가리키지 않는다.
-- 앱을 지웠다 깔면 새 번호가 되므로 「사람 수」가 아니라 「기기 수에 가까운 값」이다.
CREATE TABLE anonymous_activity_day (
    session_id  UUID        NOT NULL,
    day         DATE        NOT NULL,
    source      VARCHAR(10) NOT NULL DEFAULT 'REQUEST',
    CONSTRAINT pk_anonymous_activity_day PRIMARY KEY (session_id, day),
    CONSTRAINT ck_anonymous_activity_day_source CHECK (source IN ('REQUEST', 'BACKFILL'))
);
CREATE INDEX ix_anonymous_activity_day_day ON anonymous_activity_day (day);

-- 거꾸로 채우기 — 이미 있는 기록에서 「그날 왔다」가 확실한 것만 쓴다.
-- 빠지는 날이 있다(토큰을 갱신하지 않고 앱만 연 날 등). 그래서 이 날짜들의 DAU 는 실제보다 작다 — 문서에 적는다.
INSERT INTO user_activity_day (user_id, day, source)
SELECT DISTINCT user_id, day, 'BACKFILL' FROM (
    SELECT user_id, (created_at AT TIME ZONE 'Asia/Seoul')::date AS day FROM app_user
    UNION ALL SELECT s.user_id, (s.created_at AT TIME ZONE 'Asia/Seoul')::date FROM auth_session s
    UNION ALL SELECT s.user_id, (r.created_at AT TIME ZONE 'Asia/Seoul')::date
        FROM auth_refresh_token r JOIN auth_session s ON s.session_id = r.session_id
    UNION ALL SELECT owner_user_id, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM trip WHERE owner_type = 'USER'
    UNION ALL SELECT created_by, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM itinerary_versions
    UNION ALL SELECT author_user_id, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM route_post
    UNION ALL SELECT suggester_user_id, (created_at AT TIME ZONE 'Asia/Seoul')::date FROM place_suggestion
) t
WHERE EXISTS (SELECT 1 FROM app_user u WHERE u.user_id = t.user_id)
ON CONFLICT DO NOTHING;

INSERT INTO anonymous_activity_day (session_id, day, source)
SELECT DISTINCT session_id, day, 'BACKFILL' FROM (
    SELECT session_id, (created_at AT TIME ZONE 'Asia/Seoul')::date AS day FROM anonymous_session
    UNION ALL SELECT session_id, (last_seen_at AT TIME ZONE 'Asia/Seoul')::date FROM anonymous_session
) t
ON CONFLICT DO NOTHING;
