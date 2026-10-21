-- 구간에 「길의 어디가 가파르고 어디가 계단인가」(경사·계단 조각)를 담는다.
--
-- 🔴 왜 필요한가. 경로 API(/routes)는 걷는 길을 경사·계단이 같은 조각(pieces)으로 나눠 주는데,
--    일정 구간(itinerary_leg)은 선형(path)만 저장하고 조각은 버렸다. 그래서 일정 화면은 걷는 길을
--    그릴 수는 있어도 어디가 가파르고 어디가 계단인지 칠할 수 없었다. 오르막(ascent_m)·계단 칸 수
--    (stair_steps) 칸은 있지만 아무도 채우지 않는다 — 보행 그래프는 계단이 「있는지」만 알고 몇 칸인지는 모른다.
--
-- 모양은 경로 API 의 pieces 와 같다: [{"from":0,"to":3,"slopePercent":2.5,"stairs":false}, …].
-- from·to 는 path 배열의 자리(둘 다 포함)이고, slopePercent 는 방향 없는 기울기(%)이며 모르면 null 이다.
-- JSONB 로 두는 이유는 path 와 같다(V20260922160000__itinerary_leg_path.sql) — 통째로 쓰이거나 안 쓰인다.
--
-- 기존 행은 NULL 로 남는다. 그때 어느 조각이 가팔랐는지는 알 수 없고, 모르는 경사를 0(평지)으로
-- 적으면 「평지 길」이라는 거짓이 된다.
ALTER TABLE itinerary_leg
    ADD COLUMN pieces JSONB;

-- 조각은 path 의 자리를 가리키므로 path 없이 있을 수 없다. 빈 배열은 「없다」를 「잰 결과가 비었다」로
-- 바꿔 적는 것이라 막는다 — 없으면 NULL 이다. (path 의 ck_itinerary_leg_path 와 같은 규칙이다.)
ALTER TABLE itinerary_leg
    ADD CONSTRAINT ck_itinerary_leg_pieces
        CHECK (pieces IS NULL
            OR (path IS NOT NULL AND jsonb_typeof(pieces) = 'array' AND jsonb_array_length(pieces) >= 1));

COMMENT ON COLUMN itinerary_leg.pieces IS
    '이 구간 길의 경사·계단 조각. [{"from","to","slopePercent","stairs"}, …], from·to 는 path 의 자리. NULL=모른다';
