-- S15P21E201-179 — 구간의 이동시간이 실제 값인지 어림값인지를 남긴다.
--
-- 🔴 왜 필요한가. 지금까지 itinerary_leg.duration_min 은 항상 NULL 이었고 distance_m 은
--    직선거리였다. 이제 카카오 길찾기로 실제 값을 채우는데, 외부 응답은 호출 한도·장애·
--    권역 밖 좌표로 언제든 빈다. 그때 직선거리로 어림잡아 채우되 **어림값이라는 사실이
--    값과 함께 남아야 한다** — 표시 없이 채우면 그건 추정이 아니라 창작이고, 화면은
--    그것을 실제 소요시간으로 그린다.
--
-- 🔴 boolean 이 아니라 itinerary_item.data_status 와 **같은 낱말**을 쓴다. 참·거짓으로는
--    "어림잡았다" 와 "좌표가 없어 아무것도 못 쟀다" 를 구분할 수 없는데, 그 둘은 화면에
--    서로 다른 것을 그려야 한다(앞은 "예상 25분", 뒤는 아무것도 안 띄운다).
--
-- 기존 행은 NULL 로 남는다. 그 값들은 이 기능이 생기기 전에 만들어진 것이라 무엇이었는지
-- 알 수 없다 — 모르는 것을 UNKNOWN 으로 적는 것도 하나의 주장이므로, 아예 비워 둔다.
ALTER TABLE itinerary_leg
    ADD COLUMN data_status VARCHAR(20);

ALTER TABLE itinerary_leg
    ADD CONSTRAINT ck_itinerary_leg_data_status
        CHECK (data_status IS NULL OR data_status IN ('VERIFIED', 'ESTIMATED', 'UNKNOWN'));

COMMENT ON COLUMN itinerary_leg.data_status IS
    'VERIFIED=길찾기 실제 응답, ESTIMATED=직선거리 어림값, UNKNOWN=좌표가 없어 못 쟀다, NULL=이 기능 이전에 만들어진 행';
