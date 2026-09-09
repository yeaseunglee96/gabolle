-- S15P21E201-357 · -166 — 개인정보 자동 정리 배치의 실행 기록.
--
-- 이 표는 배치가 지운 행을 담지 않는다(지운 것은 이미 없다). 대신 "언제 돌았고, 무엇을
-- 몇 건 지웠고, 실패했는가" 만 남긴다 — 배치가 조용히 넘어가지 않았다는 증거다.
--
-- 🔴 새 마이그레이션 번호는 파일 목록의 마지막 것보다 높게 잡는다(2026-09-07 -689/-690
--    마이그레이션 주석의 INC-DEPLOY-002 · DEC-DB-003). 이 파일을 만들 때 가장 높은 파일이
--    V20260908150000 이라 그보다 높은 V20260908160000 으로 잡았다.

CREATE TABLE privacy_cleanup_run (
    run_id                    UUID          PRIMARY KEY,
    started_at                TIMESTAMPTZ   NOT NULL,
    finished_at               TIMESTAMPTZ,
    status                    VARCHAR(10)   NOT NULL,

    expired_sessions_deleted  INTEGER       NOT NULL DEFAULT 0,
    expired_refresh_tokens_deleted INTEGER  NOT NULL DEFAULT 0,
    expired_events_deleted    INTEGER       NOT NULL DEFAULT 0,

    -- 실패했을 때만 채워진다. 성공했으면 NULL.
    error_message             TEXT,

    CONSTRAINT ck_privacy_cleanup_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

-- 최근 실행을 시각 역순으로 훑는 조회(운영 확인·다음 실행 판단)가 기준으로 삼는 색인.
CREATE INDEX ix_privacy_cleanup_run_started_at ON privacy_cleanup_run (started_at DESC);

COMMENT ON TABLE privacy_cleanup_run IS
    '개인정보 자동 정리 배치(S15P21E201-357)의 실행 이력 — 성공/실패와 카테고리별 삭제 건수.';
