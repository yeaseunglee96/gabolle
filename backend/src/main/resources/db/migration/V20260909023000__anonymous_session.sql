-- S15P21E201-303 — 익명 출입증(anonymous session) 표.
--
-- 가입 안 한 사람이 여행을 만들면, 그 요청이 끝나는 순간 주인을 증명할 방법이 없어진다.
-- 서버가 무작위 문자열(출입증)을 발급하고 그 해시만 들고 있다가, 다음 요청의
-- X-Session-Token 헤더를 같은 방식으로 해시해 대조하면 주인을 다시 찾을 수 있다.
--
-- 🔴 원본 문자열은 어디에도 저장하지 않는다. token_hash 는 SessionTokenGenerator.hash()
-- (SHA-256 hex, 64자)가 만든 값이고, 다른 auth 표(auth_session.refresh_token_hash 등)와
-- 같은 자리·같은 길이 관례를 따른다.

CREATE TABLE anonymous_session (
    session_id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_anonymous_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_anonymous_session_last_seen ON anonymous_session (last_seen_at);
