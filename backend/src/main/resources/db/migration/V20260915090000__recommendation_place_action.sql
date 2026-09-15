-- S15P21E201-1013 — 추천 후보의 저장·제외를 서버에 남긴다.
--
-- 지금은 기기(AsyncStorage)에만 있다. 기기를 바꾸거나 앱을 지우면 사라진다
-- (frontend/src/plan/recommendationActions.ts — 그 파일이 "서버에 저장하지 않는다.
-- 추천 후보의 저장·제외를 받는 API 가 아직 없다" 고 적어 뒀다). 이 표가 그 API 의 자리다.
--
-- 🔴 여행마다 따로 적는다 — 장소 하나에 판단 하나가 아니다.
--    저장·제외는 "이 장소가 좋다" 가 아니라 "이번 여행의 후보로 좋다" 이다. 한 곳에 몰아
--    적으면 다른 여행에서 제외한 장소가 이번 여행에서도 제외된 것처럼 보인다. 프론트가
--    기기에 적을 때 이미 여행별로 나눠 뒀고, 서버도 같은 모양이어야 옮겨 담을 수 있다.
--
-- 🔴 itinerary_excluded_place 를 재사용하지 않는다 — 다른 것이다.
--    그 표는 "만들어진 일정에서 뺀 장소" 이고 itinerary_version_id 에 매달려 있다.
--    이 표는 "추천 후보에 대한 판단" 이라 일정이 아직 없어도 생긴다. 둘을 합치면 일정을
--    새로 만들 때마다 사용자의 판단이 사라지거나, 판단 때문에 일정 이력이 더러워진다.
--
-- 🔴 사람마다 따로 적는다 (user_id 가 키에 있다).
--    여행은 여럿이 함께 쓰지만, 지금 기기에 적히는 값은 그 기기 = 그 사람의 판단이다.
--    표를 여행 단위로만 두면 옮겨 담는 순간 뜻이 바뀐다 — 동행자 한 명이 제외한 후보가
--    말없이 모두에게서 사라진다. 그건 이 티켓이 요청한 것이 아니라 새 제품 결정이다.
--    함께 쓰기로 정하면 그때 user_id 를 키에서 빼면 되고, 그 방향이 되돌리기 쉽다.

CREATE TABLE recommendation_place_action (
    recommendation_place_action_id UUID        PRIMARY KEY,
    user_id                        UUID        NOT NULL,
    trip_id                        UUID        NOT NULL,
    place_id                       UUID        NOT NULL,
    -- SAVED = 담아 둔다, EXCLUDED = 이번 여행 후보에서 뺀다.
    action                         VARCHAR(20) NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_recommendation_place_action_user
        FOREIGN KEY (user_id)  REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_place_action_trip
        FOREIGN KEY (trip_id)  REFERENCES trip (trip_id)     ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_place_action_place
        FOREIGN KEY (place_id) REFERENCES place (place_id)   ON DELETE CASCADE,

    -- 🔴 한 사람이 한 여행에서 한 장소에 대해 갖는 판단은 하나뿐이다. 저장했다가 제외로
    --    바꾸면 새 행이 생기는 것이 아니라 그 행이 바뀐다. 이 제약이 없으면 같은 장소가
    --    저장이면서 동시에 제외인 상태가 만들어지고, 화면은 둘 중 아무거나 그린다.
    CONSTRAINT uk_recommendation_place_action
        UNIQUE (user_id, trip_id, place_id),

    CONSTRAINT ck_recommendation_place_action_action
        CHECK (action IN ('SAVED', 'EXCLUDED'))
);

-- 🔴 조회용 색인을 따로 만들지 않는다.
--    화면이 부르는 것은 "이 여행에서 내가 내린 판단 전부" (user_id = ? AND trip_id = ?) 인데,
--    위 UNIQUE 제약이 만드는 색인이 (user_id, trip_id, place_id) 라 그 질의를 앞 두 칸으로
--    그대로 받는다. 같은 일을 하는 색인을 하나 더 만들면 넣고 고칠 때마다 둘을 갱신한다.

COMMENT ON TABLE recommendation_place_action IS
    '추천 후보에 대한 사용자별·여행별 판단(담아두기/빼기). 일정에서 뺀 장소(itinerary_excluded_place)와 다르다.';
