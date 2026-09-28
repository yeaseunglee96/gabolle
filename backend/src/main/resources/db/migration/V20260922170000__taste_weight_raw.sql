-- 눌러 담기 «전» 의 합을 남긴다 (S15P21E201-1500).
--
-- 🔴 왜 필요한가
--
-- 행동 무게는 두 단계로 만들어진다.
--
--     raw    = 기여값들의 합               (하트 +1.0 · 방문 +0.5 · 봄 +0.1 · 일정제거 -0.5)
--     weight = raw / (abs(raw) + K)        K = 3.0, BehaviorTasteFolder.CONFIDENCE_K
--
-- 지금 표에 남는 것은 «눌러 담은» weight 뿐이고 raw 는 매번 이벤트에서 다시 만들어 쓰고
-- 버린다. 배치는 그래도 된다 — 접을 때마다 전 이력을 다시 읽으니까.
--
-- 그런데 카프카 소비자는 이벤트 하나만 들고 온다. 하트 하나를 더하려면 «지금까지의 합» 에
-- 더해야 하는데, weight 만 있으면 그 합이 얼마였는지를 모른다. 눌러 담은 값에 기여값을
-- 더하는 것은 뜻이 없다 — 0.4 에 1.0 을 더하면 1.4 이지 「하트가 하나 늘었다」가 아니다.
--
-- 🔴 결과는 안 바뀐다
--
-- 칸이 하나 느는 것뿐이고, weight 는 지금 그대로 적힌다. 읽는 쪽(추천 채점)은 weight 만
-- 보므로 이 마이그레이션으로 점수가 달라질 수 없다.
--
-- 🔴 기존 행은 되돌려서 채운다
--
-- weight 에서 raw 를 정확히 되돌릴 수 있다. 위 식이 일대일이라서다.
--
--     w = raw/(abs(raw)+K)  →  raw = K*w / (1 - abs(w))
--
-- 안 채우고 0 으로 두면, 다음 배치가 그 사람을 다시 접기 전까지 소비자가 「지금까지의 합이
-- 0」 으로 알고 더한다 — 쌓아 둔 취향이 잠깐 사라진다.
--
-- 🔴 INTERACTION 행만 채운다
--
-- BLENDED 행의 weight 는 clamp(설문 + 행동) 이라 행동 몫만 떼어낼 수 없다 (S15P21E201-1499
-- 이전에 적힌 행들이다). 그 행들은 되돌리면 «설문까지 행동인 것처럼» 부풀려지므로 손대지
-- 않고 0 으로 둔다. 다음 배치가 그 사용자를 접을 때 설문 행과 행동 행으로 나뉘어 새로
-- 적히고, 그때 제 값이 들어간다.
--
-- SURVEY 행도 0 이다. 설문 무게는 사람이 고른 값이지 관측의 합이 아니라, raw 라는 개념이
-- 아예 없다.

ALTER TABLE user_taste_weight
    ADD COLUMN raw DOUBLE PRECISION NOT NULL DEFAULT 0;

-- abs(weight) = 1 이면 0 으로 나눈다. confidence() 는 1 에 절대 못 닿지만
-- (raw/(abs(raw)+3) < 1), 옛 BLENDED 행은 clamp 로 만들어져 정확히 1.0 일 수 있다.
-- 위 주석대로 그 행들은 어차피 대상이 아니라 조건 둘이 함께 막는다.
UPDATE user_taste_weight
   SET raw = (3.0 * weight) / (1.0 - abs(weight))
 WHERE evidence = 'INTERACTION'
   AND abs(weight) < 1.0;

COMMENT ON COLUMN user_taste_weight.raw IS
    '눌러 담기 전의 기여값 합. weight = raw/(abs(raw)+3). 소비자가 증분으로 더하는 자리다 (S15P21E201-1500)';
