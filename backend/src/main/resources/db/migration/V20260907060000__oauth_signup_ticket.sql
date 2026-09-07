-- S15P21E201-689 · -690 — 소셜 인증과 회원가입 사이를 잇는 티켓.
--
-- 지금까지 소셜 로그인은 인증 코드와 14세 확인·약관 동의를 한 요청에 받아 그 자리에서 계정을 만들었다.
-- 다른 서비스처럼 "인증 → 회원가입 화면(소셜 정보 미리 채움) → 완료" 로 가려면 두 요청 사이에
-- provider 가 준 신원(누구인지)을 서버가 잠깐 들고 있어야 한다. 그 자리가 이 표다.
--
-- 🔴 지키는 약속 셋
-- (1) 클라이언트에는 원문 티켓(43글자 난수)만 주고 서버에는 SHA-256 해시만 남는다. DB 가 새어도
--     티켓으로 가입을 가로챌 수 없다 — auth_one_time_token 과 같은 방식이다.
-- (2) 10분 뒤 만료, 한 번 쓰면 consumed_at 이 찍힌다. 같은 티켓으로 두 번 가입할 수 없다.
-- (3) provider 의 access token 은 저장하지 않는다. 필요한 것은 신원 번호·이메일·이름·언어뿐이다.
--
-- auth_one_time_token 을 재사용하지 않은 이유 — 그 표는 local_credential 에 NOT NULL 로 매달려 있다.
-- 소셜로 처음 온 사람에게는 아직 계정도 자격증명도 없다.

CREATE TABLE oauth_signup_ticket (
    oauth_ticket_id   UUID          PRIMARY KEY,

    -- SIGNUP: 처음 보는 소셜 신원, 가입 화면으로 이어진다.
    -- LINK:   같은 이메일의 로컬 계정이 있다. 비밀번호를 확인하고 그 계정에 신원을 붙인다.
    kind              VARCHAR(10)   NOT NULL,
    ticket_hash       VARCHAR(64)   NOT NULL,

    provider          VARCHAR(20)   NOT NULL,
    provider_subject  VARCHAR(255)  NOT NULL,
    -- provider 가 이메일을 안 주면(카카오 기본 동의) NULL. 그 계정은 이메일 없이 만들어진다.
    provider_email    VARCHAR(254),
    display_name      VARCHAR(50),
    language          VARCHAR(2),

    -- LINK 일 때만: 붙일 기존 계정. 그 계정이 지워지면 티켓도 함께 지워진다.
    existing_user_id  UUID,
    device_id         VARCHAR(255),

    created_at        TIMESTAMPTZ   NOT NULL,
    expires_at        TIMESTAMPTZ   NOT NULL,
    consumed_at       TIMESTAMPTZ,

    CONSTRAINT uk_oauth_signup_ticket_hash UNIQUE (ticket_hash),
    CONSTRAINT fk_oauth_signup_ticket_user FOREIGN KEY (existing_user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_oauth_signup_ticket_kind CHECK (kind IN ('SIGNUP', 'LINK')),
    CONSTRAINT ck_oauth_signup_ticket_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_oauth_signup_ticket_link_user CHECK (kind <> 'LINK' OR existing_user_id IS NOT NULL)
);

CREATE INDEX ix_oauth_signup_ticket_expires ON oauth_signup_ticket (expires_at);

COMMENT ON TABLE oauth_signup_ticket IS
    '소셜 인증과 회원가입(또는 기존 계정 연결) 사이를 잇는 10분짜리 1회용 티켓. 해시만 저장한다.';

-- 소셜로만 가입한 계정은 이메일이 없을 수 있다(카카오). auth_identity.provider_email 은 원래 NULL 허용이라
-- 표는 고칠 것이 없다 — 이 줄은 그 사실을 다음 사람이 찾지 않게 적어 두는 것이다.
