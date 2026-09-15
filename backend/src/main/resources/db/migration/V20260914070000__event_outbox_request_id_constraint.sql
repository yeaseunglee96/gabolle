-- S15P21E201-947 — event_outbox.request_id 의 배타 규칙을 DB 제약으로도 지킨다.
--
-- V20260903123000(event_outbox_join_axes)의 2026-09-03 결정: 추천 이벤트
-- (aggregate_type = 'recommendation')는 요청 축이 이미 aggregate_id 다 — request_id 칸은
-- 그 이벤트에서는 절대 채우지 않는다. 채우면 같은 값이 두 칸에 있다가 나중에 하나만
-- 고쳐질 때 둘이 어긋난다.
--
-- 🔴 지금까지 이 규칙은 OutboxAppendCommand 생성자(compact constructor)의 자바 코드
-- 한 곳에서만 지켜졌다 — event_outbox 에 새 행을 넣는 경로가 지금은 그 클래스 하나뿐이라
-- 실제로는 안전하지만, DB 는 그것을 몰랐다. 나중에 배치 백필이나 마이그레이션 스크립트가
-- 그 클래스를 거치지 않고 이 표에 바로 쓰면 같은 사고(같은 요청이 두 칸에 다른 값으로
-- 남는 것)가 조용히 재현된다.
--
-- 반대 방향("추천 축 이벤트는 aggregate_id 가 비어 있으면 안 된다")은 aggregate_id 가
-- 이미 NOT NULL 이라 DB 가 따로 더 지킬 것이 없다 — 그 축에서는 aggregate_id 자체가
-- request_id 이기 때문이다(EventIngestService.aggregateIdOf, RECOMMENDATION_REQUEST 분기).
ALTER TABLE event_outbox
    ADD CONSTRAINT ck_event_outbox_request_id_excludes_recommendation
        CHECK (NOT (aggregate_type = 'recommendation' AND request_id IS NOT NULL));
