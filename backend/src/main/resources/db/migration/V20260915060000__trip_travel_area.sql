-- S15P21E201-980 — 여행 범위(해운대·광안리·남포동·서면·영도·송정).
--
-- 기본 정보 화면의 지역 칩이 화면에서만 받고 서버로 오지 않았다. 고른 사람은 반영됐다고
-- 믿지만 아무 영향이 없었다 — 해운대를 골라도 추천 스무 곳이 전부 출발지 근처였다.
--
-- trip 표에 배열 칸을 더하지 않고 옆 표에 둔다. trip_seed_place(V20260907130000)와 같은
-- 모양이다. 도메인 Trip 에 칸을 더하면 그 생성자를 쓰는 자리가 전부 따라 바뀌는데, 읽는
-- 쪽이 추천 엔진 하나뿐이라 그럴 이유가 없다.
CREATE TABLE trip_travel_area (
    trip_id    UUID        NOT NULL,

    -- 앱의 basics.tsx AREAS 와 같은 코드다. 좌표는 코드(TravelArea)가 들고 있다 — 표에
    -- 좌표를 적으면 지역 중심을 옮길 때 이미 만든 여행만 옛 좌표로 남는다.
    area_code  VARCHAR(30) NOT NULL,

    -- 고른 순서. 지금은 안 쓰지만 "먼저 고른 곳을 더 본다" 를 나중에 하려면 필요하고,
    -- 저장 시점에만 알 수 있는 값이다.
    sequence   INTEGER     NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_trip_travel_area      PRIMARY KEY (trip_id, area_code),
    CONSTRAINT fk_trip_travel_area_trip FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_travel_area_code
        CHECK (area_code IN ('HAEUNDAE', 'GWANGALLI', 'NAMPO', 'SEOMYEON', 'YEONGDO', 'SONGJEONG')),
    CONSTRAINT ck_trip_travel_area_sequence CHECK (sequence >= 1)
);

CREATE INDEX ix_trip_travel_area_trip ON trip_travel_area (trip_id);

COMMENT ON TABLE trip_travel_area IS
    '여행 범위 — 사용자가 고른 부산 지역. 추천이 이 지역들에서 후보를 고른다 (S15P21E201-980).';
