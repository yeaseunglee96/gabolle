-- 「이 사람이 이 장소에 대해 지금 어떤 상태인가」 (S15P21E201-1500).
--
-- 🔴 왜 표가 필요한가
--
-- 하트·싫어요·하트끔은 «상태» 다. 그래서 배치는 (사람, 장소) 마다 이력의 «마지막» 이벤트
-- 하나만 보고 판정한다 (S15P21E201-1506). 이력 전체를 읽을 수 있어서 가능한 일이다.
--
-- 카프카 소비자는 이벤트 하나만 들고 온다. 「하트를 껐다」가 왔을 때 얼마를 빼야 하는지는
-- «직전 상태가 무엇이었나» 에 달려 있는데, 그걸 알 방법이 없다.
--
--     직전이 하트였다  → 하트 몫(+1.0)을 뺀다
--     직전이 싫어요였다 → 싫어요 몫(-1.0)을 뺀다
--     직전이 없었다     → 뺄 것이 없다 (아무 일도 안 일어난다)
--
-- 이 표가 그 「직전」을 들고 있다.
--
-- 🔴 덤으로 중복도 같은 자리에서 막힌다
--
-- 하트 한 번이 이벤트 두 건으로 온다 — 저장 API 가 서버에서, 앱이 분석 이벤트로 한 번 더.
-- eventId 가 달라 멱등 장부로는 안 막힌다 (S15P21E201-1485). 그런데 상태로 보면 둘째 건은
-- 「이미 하트인데 또 하트」라 바뀌는 것이 없다 — 저절로 한 번만 세어진다.
--
-- 되돌리기와 중복 방지가 같은 한 곳에서 풀리는 것이 이 표를 따로 두는 이유다.
--
-- 🔴 saved_place 로 대신할 수 없다
--
-- saved_place 도 하트 상태를 안다. 그런데 그건 «지금» 의 상태이고, 소비자가 필요한 것은
-- 「취향 벡터가 지금까지 반영해 둔 상태」다. 소비자는 뒤늦게 도착한 이벤트를 처리하므로
-- 둘이 다를 수 있다. saved_place 를 보면 켰다 끈 것을 「원래 안 켰던 것」으로 읽어 뺄 몫을
-- 못 찾는다. 그리고 싫어요는 saved_place 에 아예 없다.

CREATE TABLE user_place_taste_state (
    user_id    UUID        NOT NULL,
    place_id   UUID        NOT NULL,

    -- 지금 반영돼 있는 상태. place_like · place_dislike · place_like_removed
    -- 값을 CHECK 로 막지 않는다 — 신호 목록은 EventType 이 들고 있고, 여기 적으면
    -- 목록이 둘이 되어 새 상태 이벤트를 더한 날 이쪽이 조용히 안 따라간다.
    event_type VARCHAR(64) NOT NULL,

    -- 언제 반영했나. 사람이 「왜 이 값이지」를 되짚을 때의 실마리다.
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_user_place_taste_state PRIMARY KEY (user_id, place_id),

    -- 🔴 ON DELETE CASCADE 를 «일부러» 안 건다. 탈퇴는 app_user 행을 지우지 않고
    --    익명화하므로 CASCADE 가 한 번도 안 돈다 — push_token 이 그렇게 만들어졌다가
    --    「탈퇴한 사람 폰으로 알림이 계속 간다」로 잡혔다. AccountDeletionService 가
    --    직접 지운다.
    --
    --    그래도 외래키는 건다. 이 표가 app_user 를 가리켜야
    --    AccountDeletionTableInventoryTest 가 「새 표가 생겼으니 탈퇴 때 어떻게 할지
    --    정하라」고 빨개진다. 안 걸면 그 안전망 밖에 놓인다.
    CONSTRAINT fk_user_place_taste_state_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);

COMMENT ON TABLE user_place_taste_state IS
    '취향 벡터가 (사람, 장소) 에 대해 지금 반영해 둔 상태. 소비자가 증분할 때 «직전» 을 여기서 읽는다 (S15P21E201-1500)';
