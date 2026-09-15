-- ══════════════════════════════════════════════════════════════════════════════
-- 사용자 차단 — S15P21E201-990
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 번호를 날짜로 지어내지 않았다. 머지 직전에 대상 브랜치의 최대 번호를 다시 확인한다 —
--    Flyway 순서가 역전되어 운영 기동이 막힌 적이 있다(S15P21E201-966, 되돌리기까지 갔다).
--    2026-09-15 확인: 모든 브랜치를 통틀어 최대가 V20260915060000 이었다.
--
-- 🔴 차단의 뜻이 흔한 것과 반대다. 여기서 **A 가 B 를 차단하면 B 가 A 를 못 본다.**
--    "내가 이 사람을 안 본다" 가 아니라 "이 사람에게 내 것을 안 보여준다" 이다.
--    그래서 조회는 늘 `blocked_user_id = 나` 로 들어온다 — "나를 차단한 사람이 누구인가".
--    색인을 그 방향으로 거는 이유가 이것이다.
--
--    애플 심사 지침 1.2 는 "The ability to block abusive users from the service" 만
--    요구하고 방향을 정하지 않는다. 이 방향은 팀이 고른 것이다.

CREATE TABLE user_block (
    blocker_user_id  UUID         NOT NULL,
    blocked_user_id  UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,

    -- 🔴 한 쌍은 한 줄. 버튼을 빠르게 두 번 눌러도 두 줄이 생기지 않는 것은 화면이 아니라
    --    이 제약이 보장한다 (user_follow 가 같은 이유로 같은 모양이다).
    CONSTRAINT pk_user_block PRIMARY KEY (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_user_block_blocker FOREIGN KEY (blocker_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_block_blocked FOREIGN KEY (blocked_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    -- 자기 자신은 차단할 수 없다.
    CONSTRAINT ck_user_block_not_self CHECK (blocker_user_id <> blocked_user_id)
);

-- 🔴 방향이 중요하다. 피드 질의는 "나를 차단한 사람들" 을 찾는다:
--       NOT EXISTS (SELECT 1 FROM user_block b
--                    WHERE b.blocker_user_id = s.author_user_id AND b.blocked_user_id = :me)
--    기본키가 (blocker, blocked) 라 blocked 로 시작하는 조회는 그 키를 못 탄다.
--    그래서 blocked_user_id 쪽에 색인을 따로 둔다. user_follow 가 followee 에 색인을
--    따로 둔 것과 같은 자리다.
CREATE INDEX ix_user_block_blocked ON user_block (blocked_user_id);

COMMENT ON TABLE user_block IS
    '사용자 차단. blocker 가 blocked 를 차단하면 blocked 는 blocker 의 기록·프로필을 볼 수 없다. 반대 방향은 막지 않는다 — S15P21E201-990.';
