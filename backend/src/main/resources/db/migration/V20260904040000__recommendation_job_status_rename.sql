-- S15P21E201-192 — JobStatus 이름을 API 명세서(v1.2, 2026-09-02 18:00 확정)와 맞춘다.
--
-- V20260902090000 이 만든 ck_recommendation_job_status 는 'QUEUED'·'CANCELED'(한 글자 L)를
-- 썼다. 그 자리의 주석이 "GB-API-001 5장 그대로다" 라고 적었는데, 실제로 잠긴 명세서는
-- PENDING · CANCELLED(두 글자 L) 다 — 명세서가 갱신되기 전 버전을 보고 쓴 주석이었다.
--
-- 🔴 recommendation_job 은 이 티켓 전까지 컨트롤러가 없어 실제로 쓰인 적이 없다(고지혁 님
-- 확인, 2026-09-04). 그래서 데이터 백필 없이 CHECK 만 바꾼다 — 남은 QUEUED/CANCELED 행이
-- 있었다면 UPDATE 가 먼저였겠지만, 없으므로 그 단계를 생략하는 것이 아니라 애초에 필요가
-- 없다.

ALTER TABLE recommendation_job
    DROP CONSTRAINT ck_recommendation_job_status;

ALTER TABLE recommendation_job
    ADD CONSTRAINT ck_recommendation_job_status
        CHECK (job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'));
