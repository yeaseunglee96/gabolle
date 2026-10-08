-- S15P21E201-2017 — 일정 조정: 「순서 고정」/「시각 고정」.
--
-- 1. itinerary_item.lock_mode — 어떻게 고정했나.
--      ORDER 순서 고정 — 재계산이 이 곳을 빼지 않고 자리(순번)를 지킨다. 지금까지의 「고정」이 하던 일이다
--      TIME  시각 고정 — 위에 더해 시작 시각도 지킨다. 앞 곳이 빠지거나 짧아지면 그 앞이 빈 시각이 된다
--
--    🔴 기존 행은 고치지 않는다(NULL 그대로). 판은 덮어쓰지 않는 스냅샷이라 옛 판의 행을 UPDATE 하면 「그때 무엇이었나」가
--       소급해서 바뀐다. 대신 코드가 locked=TRUE 이고 lock_mode 가 NULL 인 행을 ORDER(순서 고정)로 읽는다
--       (ItineraryItem 생성자). 그러니 기존 고정은 전부 순서 고정으로 넘어간다 — 뜻이 그대로다.
ALTER TABLE itinerary_item ADD COLUMN lock_mode VARCHAR(5);

ALTER TABLE itinerary_item ADD CONSTRAINT ck_itinerary_item_lock_mode
    CHECK (lock_mode IS NULL OR (locked AND lock_mode IN ('ORDER', 'TIME')));

COMMENT ON COLUMN itinerary_item.lock_mode IS
    '고정 방식. ORDER=순서 고정, TIME=시각 고정. NULL 이면서 locked 면 순서 고정(이 칸 이전의 고정). S15P21E201-2017';
