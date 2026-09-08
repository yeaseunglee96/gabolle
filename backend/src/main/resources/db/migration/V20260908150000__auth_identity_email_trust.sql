-- S15P21E201-741 — 소셜 이메일을 나중에 계정 연결 판정에 쓰기 위해, "이 이메일을
-- 믿어도 되는가" 의 근거를 auth_identity 에 함께 저장한다.
--
-- 지금 auth_identity.provider_email 에는 주소만 있고 그 주소가 실제로 그 사람
-- 것인지 확인된 것인지, 지금도 유효한지는 어디에도 남지 않는다. 연결 판정(이번
-- 마이그레이션에는 없다 — 값을 저장하는 것까지다)은 이 근거 없이는 "이메일이
-- 같으니 같은 사람이다" 를 검증 없이 믿게 된다.
--
-- ── 왜 BOOLEAN 인데 NOT NULL 을 안 붙이는가 ──────────────────────────────────
--
-- 🔴 세 공급자가 주는 신호가 다르다.
--   구글  — ID 토큰의 email_verified 클레임이 참이 아니면 로그인 자체를 거부한다
--           (GoogleIdTokenVerifier). 그러니 여기까지 도달한 이메일은 항상 검증된 것이다.
--   카카오 — kakao_account.is_email_verified 와 kakao_account.is_email_valid 를 응답에서
--           그대로 읽는다. 필드가 없으면(권한 미동의 등) 모른다.
--   네이버 — 검증 여부를 알려주는 필드가 응답에 **아예 없다.** 모든 네이버 계정이 항상
--           검증됐다는 뜻이 아니라, 우리가 그것을 확인할 방법이 없다는 뜻이다.
--
-- "모른다" 와 "확인했는데 아니다" 는 다른 사실이다. 칼럼을 NOT NULL BOOLEAN 으로
-- 만들면 이 둘을 구분할 자리가 없어져서 "모른다" 를 어느 한쪽으로 채워 넣어야 하고,
-- 그 채운 값은 나중에 "네이버가 실제로 이렇게 응답했다" 로 오독된다. 그래서 NULL 을
-- 세 번째 상태로 남긴다 — NULL 이 "모른다" 다.
--
-- ── 기존 행을 되메우지 않는 이유 ─────────────────────────────────────────────
--
-- 기존 auth_identity 행은 이 두 칼럼이 생기기 전에 이미 연결된 것들이라, 그 시점에
-- 검증 여부를 실제로 확인한 적이 없다. 지금 와서 "구글이니 true 였겠지" 하고 채우면
-- 그것도 지어낸 값이다 — 이 칼럼이 막으려는 바로 그 실수를 마이그레이션이 저지르는
-- 셈이다. 그래서 기존 행은 전부 NULL 로 남기고, 다음에 그 신원으로 로그인할 때
-- 실제 공급자 응답으로 채운다(AuthIdentity 의 갱신 메서드가 그 자리다).
ALTER TABLE auth_identity
    ADD COLUMN email_verified BOOLEAN,
    ADD COLUMN email_valid BOOLEAN;

COMMENT ON COLUMN auth_identity.email_verified IS
    'S15P21E201-741 — provider_email 이 공급자에게 소유·인증 확인을 받은 주소인지. NULL = 공급자가 이 정보를 안 주거나 우리가 아직 확인한 적이 없어 모른다는 뜻이지 false 가 아니다. 구글은 검증 실패 시 로그인 자체가 막히므로 도달한 값은 항상 true, 카카오는 kakao_account.is_email_verified 를 그대로 옮긴 값, 네이버는 이 필드 자체가 없어 항상 NULL.';
COMMENT ON COLUMN auth_identity.email_valid IS
    'S15P21E201-741 — provider_email 이 지금도 그 계정에서 유효한 주소인지(탈퇴·변경 등으로 무효화되지 않았는지). NULL = 모름. 카카오는 kakao_account.is_email_valid 를 그대로 옮긴 값이고, 네이버는 이 필드 자체가 없어 항상 NULL.';
