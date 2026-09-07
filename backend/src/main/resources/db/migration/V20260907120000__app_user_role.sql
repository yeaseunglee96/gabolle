-- S15P21E201-686 — 관리자(ADMIN) Role 도입.
-- 기존 계정은 전부 USER로 시작한다. ADMIN 승격은 이 마이그레이션이 아니라
-- 운영자가 배포 뒤 DB에서 직접 한다(이 파일에는 특정 계정을 담지 않는다).
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
