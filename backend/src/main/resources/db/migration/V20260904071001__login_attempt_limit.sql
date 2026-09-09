-- S15P21E201-421 — 로그인 연속 실패를 세고 잠근다.
--
-- 🔴 왜 지금인가. 이메일 로그인은 배포에서 실제로 도는 유일한 인증 경로인데, 지금은 같은 계정에
--    비밀번호를 몇 번이든 넣어 볼 수 있다. 프런트는 이미 429 를 받아 "요청이 너무 많아요" 를
--    띄우는 코드를 갖고 있는데(sign-in.tsx) 서버가 그 응답을 절대 내지 않는다 — 계약의 반쪽만
--    있는 상태다.
--
-- 🔴 왜 여기(local_credential)에 두는가. 셀 자리가 셋이었다.
--
--    (1) 애플리케이션 메모리 — 배포할 때마다 초기화된다. 이 저장소는 back/dev 머지마다 재배포되고
--        2026-09-04 하루에만 여러 번 있었다. 정작 공격이 이어지는 동안 비어 있을 수 있다.
--    (2) Redis 같은 별도 저장소 — 이 프로젝트에 Redis 가 없다. 이 기능 하나로 새 서버를 들이면
--        운영 대상이 늘고, 티켓이 요구하는 "저장소가 죽어도 로그인은 된다" 를 따로 만들어야 한다.
--    (3) 여기 — 세션·일회용 토큰·refresh token 이 이미 전부 DB 에 있다. 인증 상태를 한곳에 두는
--        기존 방식과 같고, 재기동과 인스턴스 증설을 견딘다.
--
--    (3) 을 골랐다. 부수 효과로 티켓 완료 기준 "카운터 저장소가 죽어도 로그인은 동작한다" 가
--    자동으로 지켜진다 — 새로 만든 저장소가 없으니 새로 죽을 것도 없다. Redis 를 골랐다면
--    fail-open(저장소가 죽으면 검사를 건너뛴다)을 따로 만들어야 했을 자리다.
--
-- 🔴 대가도 적어 둔다. 로그인이 실패할 때마다 UPDATE 가 한 번 나간다. 성공 경로에는 실패 횟수가
--    0 이 아닐 때만 나간다. 로그인 빈도에서 이 비용은 무시할 수준이고, 이것이 문제가 될 규모라면
--    그때는 Redis 를 도입할 이유가 따로 생긴 것이다.

ALTER TABLE local_credential
    -- 마지막 성공 이후 연속으로 틀린 횟수. 성공하면 0 으로 돌아간다.
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    -- 이 시각 전까지는 비밀번호가 맞아도 거부한다. 잠겨 있지 않으면 NULL 이다.
    ADD COLUMN login_locked_until TIMESTAMPTZ;

-- 🔴 음수 횟수는 계산이 어딘가 틀렸다는 뜻이다. 조용히 넘어가면 잠금이 영영 안 걸린다.
ALTER TABLE local_credential
    ADD CONSTRAINT ck_local_credential_failed_attempts_not_negative
        CHECK (failed_login_attempts >= 0);

COMMENT ON COLUMN local_credential.failed_login_attempts IS
    'S15P21E201-421. 마지막 성공 이후 연속 실패 횟수. 성공하면 0 으로 되돌린다.';
COMMENT ON COLUMN local_credential.login_locked_until IS
    'S15P21E201-421. 이 시각까지 로그인을 거부한다. 🔴 영구 잠금은 만들지 않는다 — 남의 계정을 일부러 잠가 못 쓰게 만드는 길이 되기 때문이다.';
