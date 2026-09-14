-- S15P21E201-944 — 추천 Job 생성에 재시도 방지 장치를 더한다.
--
-- 🔴 지금까지 없었던 것 — 재시도로 같은 요청이 두 번 오면 Job 이 두 개 생겼다
--    (RecommendationJobController.create javadoc이 이미 그렇게 적어 두고 있었다).
--
-- trip_idempotency(V20260904030000)와 정확히 같은 모양이다 — 그 표가 이미 같은 문제를
-- (사용자, 멱등 키) 단위로 풀어 뒀고, 여기서 새로 설계할 이유가 없다.
--
-- 🔴 job_id 에 FK 를 걸지 않는다. trip_idempotency 가 trip_id 에 FK 를 안 거는 것과 같은
-- 이유다 — 키 확보(이 표에 행을 넣는 것)와 Job 저장이 한 동작이어야 동시 요청이 전부
-- Job 을 만드는 레이스를 막을 수 있는데, INSERT 순서상 이 표의 행이 recommendation_job
-- 행보다 먼저(또는 같은 트랜잭션 안에서) 들어간다. FK 를 걸면 그 순서를 강제로 뒤집어야
-- 하고, 그러면 "키를 확보했는데 Job 은 아직 없는" 순간을 SAVEPOINT 없이 처리할 수 없다
-- (JpaItineraryRepository 가 이미 겪은 문제 — 이 저장소는 SAVEPOINT/PROPAGATION_NESTED
-- 대신 ON CONFLICT DO NOTHING 을 쓰기로 정했다, JpaTripRepository 주석 참고).
CREATE TABLE recommendation_job_idempotency (
    user_id              UUID         NOT NULL,
    idempotency_key      VARCHAR(255) NOT NULL,
    -- SHA-256 hex = 64자 (RecommendationJobRunner.fingerprintOf).
    request_fingerprint  VARCHAR(64)  NOT NULL,
    job_id               UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (user_id, idempotency_key)
);
