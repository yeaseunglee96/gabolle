-- S15P21E201-352 — event_outbox 에 조인 축과 순서 키를 더한다. 모진성 님 요청.
--
-- 왜 필요한가. 통합(225c915) 뒤 추천 이벤트의 requestId 는 aggregate_id 에 실린다.
-- 그런데 aggregate 가 요청이 아닌 이벤트가 있다 — place_like 의 aggregate_id 는 placeId
-- 이고, 그러면 requestId 가 갈 자리가 없다. -542 14장이 요구하는 "후보 → 노출을
-- request_id + place_id 로 조인" 이 그 이벤트들에서는 성립하지 않는다.
--
-- ── 어느 칸이 정본인가 (2026-09-03 결정) ──────────────────────────────────────
--
-- 🔴 추천 이벤트는 aggregate_id 가 정본이다. request_id 칸은 aggregate 축이 다른
--    이벤트에서만 채운다. 즉 두 칸이 같은 값을 갖는 행은 없다.
--
-- 후보가 둘 있었다.
--   (가) request_id 를 정본으로 두고 aggregate_id 는 그 이벤트의 대상만 담는다
--        → 조인 축이 하나가 되지만, 이미 병합된 OutboxAppendCommand 호출부를 되돌려야 한다
--   (나) aggregate_id 를 그대로 두고 request_id 는 나머지 이벤트에만 쓴다  ← 이것을 골랐다
--        → 병합된 코드를 안 건드린다. 대신 14장 조인이 aggregate_type 에 따라 갈린다
--
-- (나)를 고른 것은 -352 담당(모진성)의 판단이다. 되돌리는 비용을 아는 쪽이 정하는 것이
-- 맞다고 봤다. 대가는 정확히 하나이고 여기 적어 둔다 — **조인 쿼리 한 곳이 갈린다.**
--
--   -- 추천 요청 축으로 이을 때
--   COALESCE(request_id, CASE WHEN aggregate_type = 'RECOMMENDATION'
--                             THEN aggregate_id END)
--
-- 🔴 그 CASE 를 쓰는 자리는 S15P21E201-546(후보 → 노출 조인 검증)이다. 거기서 축을
--    한 번만 풀고, 그 뒤로는 뷰나 공통 쿼리로 감춘다. 분석 쿼리마다 이 CASE 를 다시
--    쓰면 어느 날 한 곳이 빠지고, 빠진 것은 오류가 아니라 "행이 적게 나온다" 로 나타난다.

ALTER TABLE event_outbox
    -- 🔴 aggregate 가 요청이 아닌 이벤트에서만 채운다 (place_like 등).
    --    추천 이벤트에서는 비어 있고, 그때의 축은 aggregate_id 다.
    ADD COLUMN request_id UUID,
    ADD COLUMN user_id    UUID,
    ADD COLUMN trip_id    UUID,
    -- 클라이언트가 보낸 것인가 서버가 만든 것인가 (DR-13). 뒤바뀌면 신뢰할 수 없는 값이
    -- 신뢰할 수 있는 값처럼 섞인다 — 클라이언트가 보내는 값은 조작될 수 있다.
    ADD COLUMN producer   VARCHAR(16),
    -- 🔴 순서의 보조 키. occurred_at 만으로 정렬하면 같은 시각의 이벤트 순서가 임의로
    --    떨어진다. S15P21E201-354 의 테스트가 시계를 고정해 실제로 그것을 잡았다
    --    ("받은 순서대로 나가야 한다" 가 [evt_2, evt_3, evt_1] 로 실패했다).
    --    timestamptz 는 마이크로초 단위라 부하가 걸리면 실제 DB 에서도 같은 값이 나온다.
    ADD COLUMN seq        BIGSERIAL;

-- 🔴 producer 는 값이 둘뿐이다 — Producer 열거형이 CLIENT · SERVER 둘만 갖는다.
--    요청받은 DDL 에는 이 CHECK 가 없었지만 넣었다. 문자열로 두면 'client' · 'app' 처럼
--    제각각인 값이 들어오고 아무도 모른다.
ALTER TABLE event_outbox
    ADD CONSTRAINT ck_event_outbox_producer
        CHECK (producer IS NULL OR producer IN ('CLIENT', 'SERVER'));

-- ── 색인 ──────────────────────────────────────────────────────────────────────
--
-- 🔴 기존 ix_event_outbox_pending 은 (publish_status, occurred_at) 이었는데, 릴레이가
--    실제로 던지는 질의는 publish_status 를 보지 않는다 —
--    findByPublishedAtIsNullOrderByOccurredAtAscEventIdAsc 다. 즉 그 색인은 지금
--    쓰이지 않고 있었다. 지우면서 잃는 것이 없다.
DROP INDEX ix_event_outbox_pending;

-- 조건이 붙은 색인(partial index) — 아직 안 보낸 것만 담으므로 보낸 것이 쌓여도 작게 남는다.
CREATE INDEX ix_event_outbox_pending
    ON event_outbox (seq) WHERE published_at IS NULL;

-- 🔴 위 색인은 정렬을 seq 로 하는 질의를 위한 것이다. 지금 리포지토리는 아직
--    occurred_at, event_id 순으로 정렬한다. 그 메서드를 ...OrderBySeqAsc 로 바꾸기
--    전까지는 같은 시각 이벤트의 순서 문제가 그대로 남는다 — 컬럼과 색인만으로는
--    안 고쳐진다. 자바 쪽 변경은 S15P21E201-352 에 있다.

CREATE INDEX ix_event_outbox_request
    ON event_outbox (request_id);

-- ── 이 칸들이 무엇인지 표에 적어 둔다 ─────────────────────────────────────────
COMMENT ON COLUMN event_outbox.request_id IS
    '🔴 aggregate_type 이 recommendation 이 아닌 행에서만 채워진다 (2026-09-03 결정). 추천 이벤트의 요청 축은 aggregate_id 다. 요청 축으로 이을 때는 두 칸을 함께 봐야 한다 — 그 CASE 는 S15P21E201-546 에서 한 번만 풀고 뷰로 감춘다.';
COMMENT ON COLUMN event_outbox.aggregate_id IS
    '그 이벤트의 대상 ID. 추천 이벤트에서는 request_id 가 여기 실리고 그것이 정본이다. 좋아요면 place_id, Pick 발행이면 pick_id.';
COMMENT ON COLUMN event_outbox.producer IS
    'CLIENT | SERVER (DR-13). 실제 노출·상세 조회는 클라이언트가, 좋아요·일정 편집·방문 판정은 서버 Outbox 가 만든다.';
COMMENT ON COLUMN event_outbox.seq IS
    '받은 순번. 🔴 정렬은 occurred_at 만으로 하지 않는다 — 같은 시각이면 순서가 임의로 떨어진다 (S15P21E201-354 실측).';

-- 🔴 남은 것 하나 — 통합에서 API-07 검사가 사라졌다.
--    합치기 전 OutboxEvent 의 생성자는 requestId 가 비면 예외를 던졌다("requestId 가
--    없으면 노출과 행동을 이을 수 없다"). 지금 EventOutbox 에는 그 필드도 검사도 없다.
--    이 칸을 NOT NULL 로 만들 수는 없다 — trip_created 처럼 추천 요청 없이 나는 이벤트가
--    있고, 추천 이벤트는 이 칸을 아예 비워 두기로 했다. 그래서 "어떤 이벤트에 요청 축이
--    필수인가" 는 응용 계층이 지켜야 하고, 그 자리는 S15P21E201-352 다.
--    여기 적어 두는 이유는 검사가 없어진 사실이 어디에도 안 남아 있었기 때문이다.
