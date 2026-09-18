-- S15P21E201-754 — 가게 이름을 우리 데이터로 자동완성
--
-- 🔴 이 파일은 자동으로 안 돈다. schema.sql 은 postgres 볼륨이 "처음
--    비어 있을 때"만 돈다(docker-entrypoint-initdb.d). 이미 떠 있는 설문
--    서버는 볼륨이 안 비어 있으니 schema.sql 을 다시 고쳐도 반영이 안 된다 —
--    그래서 이 ALTER 문을 손으로(또는 배포 스크립트로) 한 번 돌려야 한다.
--
-- 실행:
--   docker exec -i survey-postgres psql -U survey -d survey \
--     < survey-recommend/migrations/0001_add_place_id.sql
--
-- 무엇을 하는가 — schema.sql 의 recommendation 표에 이미 반영해 둔 것과
-- 같은 칸 둘을 기존 표에 더한다. 둘 다 NULL 허용이라 있는 행은 안 건드린다.
-- (자세한 이유는 schema.sql 의 2026-09-09 주석 참고.)

BEGIN;

ALTER TABLE recommendation ADD COLUMN IF NOT EXISTS place_id TEXT;
ALTER TABLE recommendation ADD COLUMN IF NOT EXISTS gu TEXT;

COMMIT;
