-- 갈래 열람 기록 (S15P21E201-475)
--
-- 여덟 갈래에 같은 힘을 들일 수 없다. 아무도 안 여는 갈래에 데이터를 채우는 동안 사람들이
-- 매번 여는 갈래가 비어 있을 수 있다. 기록이 없으면 그 판단을 감으로 하게 되고, 감으로 정한
-- 우선순위는 나중에 되짚을 수 없다.
--
-- 번호를 정하는 규칙은 이 폴더의 V20260907160000 머리말에 적혀 있다 — 작업을 시작한 시점의
-- 최대값이 아니라 **머지 시점의 최대값** 다음이어야 한다. 이 파일을 만들 때
-- back/dev · back/main · main 세 곳의 최대값이 모두 V20260909110000 이었다.

CREATE TABLE place_facet_view (
    place_facet_view_id UUID        PRIMARY KEY,

    -- 어느 갈래인가. 코드 문자열을 그대로 담는다 — 여기에 FK 를 걸 대상 표가 없고
    -- (갈래는 코드 열거형이다), 갈래가 늘거나 이름이 바뀌어도 옛 기록은 그때의 값으로
    -- 남아야 한다. CHECK 로 값을 묶지 않는 이유도 같다.
    facet_key           VARCHAR(40) NOT NULL,

    -- 어느 여행에서 열었나.
    --
    -- 이 기록은 익명이 아니라 가명이다 — 여행을 통해 사람에게 이어질 수 있다. 이름·이메일
    -- 같은 값은 담지 않지만 그 사실을 감추지 않는다. 티켓이 "어느 여행에서" 를 요구하고,
    -- 갈래별 이용을 여행 단위로 봐야 "한 사람이 여덟 번 연 것" 과 "여덟 사람이 한 번씩 연
    -- 것" 이 구분된다.
    --
    -- FK 를 걸고 여행이 지워지면 기록도 함께 지운다. 지워진 여행의 열람 기록만 남아 있으면
    -- 그 행은 아무 질문에도 답하지 못하면서 개인정보만 남는 셈이다.
    trip_id             UUID        NOT NULL REFERENCES trip (trip_id) ON DELETE CASCADE,

    viewed_at           TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE place_facet_view IS
    '갈래를 연 기록. 어느 갈래에 데이터를 채울지 정하는 근거로 쓴다 (S15P21E201-475).';
COMMENT ON COLUMN place_facet_view.facet_key IS
    '갈래 코드. 열거형 값을 문자열로 담는다 — 갈래가 바뀌어도 옛 기록은 그때의 값으로 남는다.';
COMMENT ON COLUMN place_facet_view.trip_id IS
    '어느 여행에서 열었나. 이 기록은 익명이 아니라 가명이다 — 여행을 통해 사람에게 이어질 수 있다.';

-- 집계는 언제나 갈래별로 센다. 그 질의 하나를 위한 색인이다.
CREATE INDEX idx_place_facet_view_facet_key ON place_facet_view (facet_key);

-- 기간을 잘라 보는 질의(예: 지난 주)를 위해 시각도 색인한다. 지금 조회 경로는 전체 기간만
-- 세지만, 기록이 쌓이면 기간 질의가 반드시 온다.
CREATE INDEX idx_place_facet_view_viewed_at ON place_facet_view (viewed_at);
