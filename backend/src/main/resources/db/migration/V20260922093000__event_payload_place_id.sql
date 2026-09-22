-- S15P21E201-1481 — 앱이 보낸 place_id 를 placeId 로 옮긴다.
--
-- 같은 place_like 이벤트가 키 두 모양으로 쌓여 있었다. 서버(SavedPlaceService)는 placeId,
-- 앱(frontend/src/analytics/appEvents.ts)은 place_id 였다.
--
-- 🔴 읽는 쪽은 placeId 하나만 본다. payload 에서 장소를 꺼내는 유일한 자리인
--    recommendation_exposure 뷰(V20260903140000__event_quality_gate.sql)가
--    payload ->> 'placeId' 로만 찾는다.
--
-- 지금 당장 새고 있는 것은 아니다 — 그 뷰는 RECOMMENDATION_IMPRESSION 만 보는데, 앱이 그
-- 이벤트를 아직 안 보낸다(S15P21E201-544). 지금 두 모양으로 쌓이는 place_like 는 아직
-- 아무도 안 읽는다.
--
-- 🔴 그래서 더 위험하다. -544 가 나가는 날 앱이 place_id 로 보내면 그 노출 이벤트의 장소
--    칸이 뷰에서 통째로 빈다. 오류가 아니라 빈 칸이라 아무 데서도 안 드러난다. 행동을 취향
--    성분으로 귀속시키는 작업도 같은 키를 읽는다.
--
-- 앞으로 들어오는 것은 EventIngestService.ingestFromClient 가 ClientPayloadKeys 로 옮긴다.
-- 이 파일은 «이미 쌓인 것»만 옮긴다.
--
-- 이벤트 표를 고쳐 쓰는 것이 원칙에는 어긋난다 — 추가만 하는 장부이기 때문이다. 그래도
-- 여기서 옮기는 이유는 셋이다.
--   1. 아직 아무 소비자도 이 행들을 안 읽었다 (Kafka consumer 는 S15P21E201-561 로 보류)
--   2. 안 옮기면 읽는 코드마다 "둘 중 아무거나" 분기가 생기고, 그 분기는 언젠가 한 곳이 빠진다
--   3. 뜻이 안 바뀐다. 키 이름만 바뀌고 값은 그대로다

-- ── 1. placeId 가 없는 행: 이름만 바꾼다 ─────────────────────────────────────
UPDATE event_outbox
   SET payload = (payload - 'place_id') || jsonb_build_object('placeId', payload -> 'place_id')
 WHERE payload ? 'place_id'
   AND NOT payload ? 'placeId';

-- ── 2. 둘 다 있고 값이 같은 행: 옛 이름만 지운다 ──────────────────────────────
--
-- 지금 이런 행은 없을 것이다(앱이 둘을 같이 보내지 않는다). 그래도 적어 두는 것은, 1번만
-- 두면 이런 행이 생겼을 때 place_id 가 영원히 남아 "옮겼다" 가 거짓이 되기 때문이다.
UPDATE event_outbox
   SET payload = payload - 'place_id'
 WHERE payload ? 'place_id'
   AND payload ? 'placeId'
   AND payload -> 'place_id' = payload -> 'placeId';

-- ── 둘 다 있고 값이 «다른» 행은 일부러 안 건드린다 ────────────────────────────
--
-- 어느 쪽이 참인지 이 자리에서 정할 수 없다. placeId 는 이미 있으므로 뷰는 정상으로 돌고,
-- 남아 있는 place_id 가 그 행이 이상하다는 표시가 된다. 앞으로 들어오는 것은
-- ClientPayloadKeys 가 400 으로 거절하므로 이런 행이 더 생기지는 않는다.

-- 이 마이그레이션은 다시 돌려도 결과가 같다 — 옮길 것이 없으면 두 UPDATE 모두 0건이다.
