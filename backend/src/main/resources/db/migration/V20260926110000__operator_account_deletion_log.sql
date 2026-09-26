-- ═════════════════════════════════════════════════
-- 운영자 대리 탈퇴 처리 기록 — S15P21E201-1647
-- ═════════════════════════════════════════════════
--
-- 🔴 왜
--    공개 삭제 안내 페이지와 자동응답이 「가입한 이메일에서 삭제 요청 메일을 보내면 7일 이내에 삭제한다」고 약속한다.
--    앱에 로그인할 수 없는 사람(앱을 지웠거나 기기를 잃음)이 보내는 요청이라 본인이 앱에서 지울 수 없고, 운영자가 대신
--    지워야 한다. 그 처리를 남기는 표다 — 「누가·언제·어느 요청을 어떻게 처리했나」.
--
-- 🔴 개인정보를 남기지 않는다
--    · 이메일 주소를 그대로 적지 않는다. 처리한 주소인지 나중에 물을 때만 쓰도록 소문자로 내린 주소의 SHA-256 만 남긴다.
--    · 요청 식별(request_ref)에는 메일 수신일·티켓 번호처럼 사람을 가리키지 않는 값만 적는다. 주소·이름을 적지 않는다.
--    · 계정 행(app_user)은 탈퇴 때 익명화만 되고 남지만, 여기 deleted_user_id 는 그 익명 행을 가리킬 뿐 누구인지 알려 주지 않는다.
--
-- 🔴 app_user 를 가리키는 외래키를 일부러 걸지 않는다
--    · 탈퇴한 사람의 처리 기록이 계정 행에 매여 지워지거나 막히면 안 된다.
--    · 걸면 「app_user 를 가리키는 표」 목록(AccountDeletionTableInventoryTest)이 늘어 탈퇴 때 어떻게 할지 다시 정해야 한다.
--      이 표는 탈퇴 대상이 아니라 탈퇴의 결과 기록이다.
--
-- outcome
--    DELETED    — 계정 하나를 찾아 지웠다(deleted_user_id 가 반드시 있다)
--    NOT_FOUND  — 그 이메일로 지금 쓸 수 있는 계정이 없다 — 이미 지웠거나 가입한 적이 없다
--    AMBIGUOUS  — 한 이메일이 서로 다른 계정 둘 이상을 가리켜 아무것도 지우지 않았다 — 사람이 가린다
--    (미리보기 DRY_RUN 은 아무것도 바꾸지 않으므로 기록하지 않는다.)

CREATE TABLE operator_account_deletion_log (
    log_id           BIGSERIAL    PRIMARY KEY,
    request_ref      VARCHAR(120) NOT NULL,
    operator_name    VARCHAR(60)  NOT NULL,
    email_sha256     VARCHAR(64)  NOT NULL,
    deleted_user_id  UUID,
    outcome          VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_operator_account_deletion_log_outcome CHECK (outcome IN ('DELETED', 'NOT_FOUND', 'AMBIGUOUS')),
    -- 지웠다는 기록에는 어느 계정인지가 늘 있고, 지우지 않은 기록에는 없다.
    CONSTRAINT ck_operator_account_deletion_log_user_pair CHECK ((outcome = 'DELETED') = (deleted_user_id IS NOT NULL)),
    CONSTRAINT ck_operator_account_deletion_log_hash_shape CHECK (email_sha256 ~ '^[0-9a-f]{64}$')
);

-- 「이 주소는 처리했나」를 묻는 길.
CREATE INDEX ix_operator_account_deletion_log_email ON operator_account_deletion_log (email_sha256);

COMMENT ON TABLE operator_account_deletion_log IS
    'S15P21E201-1647 — 운영자 대리 탈퇴 처리 기록. 이메일 원문 없이 SHA-256 만 둔다. app_user 외래키 없음(탈퇴의 결과 기록이라 계정 행에 매이지 않는다).';
COMMENT ON COLUMN operator_account_deletion_log.request_ref IS
    '요청 식별 — 메일 수신일·티켓 번호. 이메일 주소·이름을 적지 않는다.';
COMMENT ON COLUMN operator_account_deletion_log.email_sha256 IS
    '앞뒤 공백을 떼고 소문자로 내린 이메일의 SHA-256(16진 64자). 원문은 저장하지 않는다.';
