-- S15P21E201-1013 — 추천 후보의 판단을 사람별에서 여행별로 바꾼다 (동행자가 함께 본다).
--
-- 🔴 왜 앞 파일(V20260915090000)을 고치지 않고 새 파일을 쓰나
--    그 파일은 이미 back/dev 에 머지됐고, 배포된 DB 에서 이미 돌았을 수 있다. Flyway 는
--    한 번 적용한 파일의 지문(체크섬)을 flyway_schema_history 에 적어 두고 기동할 때마다
--    대조한다 — 내용이 바뀌면 "적용된 것과 파일이 다르다" 로 판단해 **서버가 아예 안 뜬다.**
--    오늘 오전 운영 502(S15P21E201-966)와 같은 종류의 고장이다. 그래서 이미 나간 파일은
--    손대지 않고, 바꾸는 일을 새 파일이 한다.
--
-- 🔴 무엇을 바꾸나
--    공유를 위해 "같이 일정짜기" 를 따로 만들지 않고, 이미 있는 여행 초대
--    (POST /api/v1/trips/{id}/invites → POST /api/v1/trip-invites/{표}/accept)를 그대로
--    공유 장치로 쓴다. 여행이 곧 공유 단위이고 사람은 초대로 들어오므로, 그 안의 판단도
--    함께 보는 것이 맞다. 그래서 키에서 사람을 뺀다.
--
--    대신 decided_by_user_id 로 마지막에 누가 정했는지를 남긴다 — 공유 상태에서 "이 후보가
--    왜 사라졌지" 에 답할 수 없으면 동행자끼리 서로를 의심하게 된다. 그 칸은 키가 아니라
--    기록이다.

-- 1) 누가 정했는지를 담을 칸을 먼저 만들고, 지금까지의 값(user_id)을 그대로 옮긴다.
--    옮기지 않고 비워 두면 "이 판단을 누가 했는지" 를 되찾을 방법이 사라진다.
ALTER TABLE recommendation_place_action
    ADD COLUMN IF NOT EXISTS decided_by_user_id UUID;

UPDATE recommendation_place_action
   SET decided_by_user_id = user_id
 WHERE decided_by_user_id IS NULL;

-- 2) 🔴 키를 좁히기 전에 부딪히는 행을 정리한다.
--    지금 키는 (사람, 여행, 장소)라 같은 여행·장소에 사람마다 다른 판단이 있을 수 있다.
--    그대로 (여행, 장소) UNIQUE 를 걸면 제약 추가 자체가 실패해 기동이 멈춘다.
--    남기는 기준은 **가장 최근에 정해진 것** 이다 — 동행자들이 차례로 의견을 냈다면 마지막
--    판단이 그 여행의 현재 결론에 가장 가깝다. updated_at 이 같으면 식별자로 확정해
--    "매번 다른 행이 남는" 일이 없게 한다.
DELETE FROM recommendation_place_action a
      USING recommendation_place_action b
      WHERE a.trip_id  = b.trip_id
        AND a.place_id = b.place_id
        AND (a.updated_at, a.recommendation_place_action_id)
          < (b.updated_at, b.recommendation_place_action_id);

-- 3) 옛 제약과 칸을 걷어낸다. 제약을 먼저 지운다 — 칸을 먼저 지우면 그 칸에 매달린
--    제약이 함께 사라져, 무엇이 지워졌는지가 이 파일만 봐서는 안 보인다.
ALTER TABLE recommendation_place_action
    DROP CONSTRAINT IF EXISTS uk_recommendation_place_action;

ALTER TABLE recommendation_place_action
    DROP CONSTRAINT IF EXISTS fk_recommendation_place_action_user;

ALTER TABLE recommendation_place_action
    DROP COLUMN IF EXISTS user_id;

-- 4) 새 모양을 건다.
--    🔴 decided_by_user_id 는 ON DELETE SET NULL 이다. 동행자 한 명이 계정을 지웠다고
--    해서 그 여행의 판단이 사라지면 안 된다 — 판단은 여행의 것이지 그 사람의 것이 아니다.
--    (옛 user_id 는 키였기 때문에 CASCADE 가 맞았다. 뜻이 달라져서 규칙도 달라진다.)
ALTER TABLE recommendation_place_action
    ADD CONSTRAINT fk_recommendation_place_action_decided_by
        FOREIGN KEY (decided_by_user_id) REFERENCES app_user (user_id) ON DELETE SET NULL;

ALTER TABLE recommendation_place_action
    ADD CONSTRAINT uk_recommendation_place_action
        UNIQUE (trip_id, place_id);

COMMENT ON TABLE recommendation_place_action IS
    '추천 후보에 대한 여행별 판단(담아두기/빼기). 동행자가 함께 본다. 일정에서 뺀 장소(itinerary_excluded_place)와 다르다.';

COMMENT ON COLUMN recommendation_place_action.decided_by_user_id IS
    '마지막으로 이 판단을 정한 사람. 키가 아니라 기록이다 — 계정을 지우면 NULL 이 되고 판단은 남는다.';
