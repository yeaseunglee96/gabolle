-- Compose 로 새 PostgreSQL 볼륨을 만들 때 백엔드 전용 schema를 준비한다.
-- 운영의 기존 DB에는 이 파일이 재실행되지 않으므로 DB 관리자가 별도로 생성한다.
CREATE SCHEMA IF NOT EXISTS gabolle AUTHORIZATION CURRENT_USER;
