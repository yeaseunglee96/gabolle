-- 기기 푸시 토큰 — S15P21E201-1391.
--
-- 앱은 로그인하면 Expo 푸시 토큰을 서버에 올린다(frontend/src/notifications/pushToken.ts,
-- S15P21E201-1429 로 이미 머지됨). 받을 자리가 없어서 지금은 그 호출이 그대로 실패한다.
--
-- 🔴 열쇠는 사람이 아니라 «토큰» 이다. 한 사람이 폰·태블릿 여러 대를 쓰므로 계정당 여러 줄이고,
--    반대로 같은 기기를 다른 사람이 쓰면(로그아웃 후 다른 계정 로그인) 토큰은 그대로인데 주인만
--    바뀐다. 그때 옛 주인 줄을 남겨 두면 «앞사람 계정의 알림이 뒷사람 폰에 뜬다» — 그래서
--    토큰을 UNIQUE 로 두고 올릴 때마다 주인을 덮어쓴다.
CREATE TABLE push_token (
    push_token_id UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL,
    -- Expo 토큰은 ExponentPushToken[...] 꼴이고 길이가 정해져 있지 않다. 길이를 박지 않는다.
    token         TEXT        NOT NULL,
    platform      VARCHAR(16) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_push_token_token UNIQUE (token),
    CONSTRAINT fk_push_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    -- 앱이 보내는 두 값만 받는다. 모르는 값이 들어오면 보낼 때가 아니라 지금 막힌다.
    CONSTRAINT ck_push_token_platform CHECK (platform IN ('ios', 'android'))
);

-- 보낼 때는 언제나 «이 사람의 기기 전부» 를 찾는다.
CREATE INDEX ix_push_token_user ON push_token (user_id);

COMMENT ON TABLE push_token IS
    '기기 푸시 토큰. 열쇠는 토큰이다 — 같은 기기를 다른 사람이 쓰면 주인만 덮어쓴다(S15P21E201-1391).';
COMMENT ON COLUMN push_token.token IS
    'Expo 푸시 토큰(ExponentPushToken[...]). UNIQUE — 한 기기가 두 사람에게 달려 있으면 남의 알림이 간다.';
COMMENT ON COLUMN push_token.updated_at IS
    '마지막으로 올라온 시각. 앱은 로그인할 때마다 올리므로 «이 기기가 아직 살아 있나» 의 단서가 된다.';
