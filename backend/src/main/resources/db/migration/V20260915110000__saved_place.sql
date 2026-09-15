-- S15P21E201-1013 — 저장한 장소(하트)를 서버에 남긴다.
--
-- 지금은 기기(AsyncStorage)에만 있다. 기기를 바꾸거나 앱을 지우면 사라진다
-- (frontend/src/discovery/savedPlaces.ts — 그 파일이 "서버 저장이 아니라 AsyncStorage 라
-- 기기를 바꾸면 안 보인다. 서버 쪽 개념이 생기면 그때 옮긴다" 고 적어 뒀다).
--
-- 🔴 recommendation_place_action 과 다른 것이다 — 이름이 비슷해 합치기 쉬운 자리다.
--
--    recommendation_place_action : "이번 여행의 후보로 담아둔다/뺀다". 여행에 매달려 있고
--                                  동행자가 함께 본다
--    saved_place (이 표)         : "이 장소가 마음에 든다". 여행과 무관한 전역 목록이고
--                                  그 사람만의 것이다
--
--    화면도 다르다. 이쪽은 홈 캐러셀의 하트와 저장 탭이고, 저쪽은 추천 결과 화면이다.
--    합치면 "다른 여행에서 담아둔 것이 홈 하트에 뜨는" 일이 생긴다.
--
-- 🔴 컬렉션은 이 표에 없다. 컬렉션에는 사용자가 직접 입력한 장소가 들어갈 수 있어서
--    (CollectionProvider.addNewPlaceToList) 장소 표를 참조하는 것만으로는 안 담긴다.
--    별도 티켓으로 간다 — 여기에 억지로 끼우면 두 기능이 서로를 제약한다.

CREATE TABLE saved_place (
    saved_place_id UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL,
    place_id       UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_saved_place_user
        FOREIGN KEY (user_id)  REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_place_place
        FOREIGN KEY (place_id) REFERENCES place (place_id)   ON DELETE CASCADE,

    -- 🔴 한 사람이 한 장소를 두 번 저장할 수 없다. 하트는 켜짐/꺼짐이라 "두 번 켠 상태" 가
    --    없다. 이 제약이 없으면 연타나 재시도 때마다 행이 쌓이고, 목록에 같은 장소가
    --    여러 번 뜬다.
    CONSTRAINT uk_saved_place UNIQUE (user_id, place_id)
);

-- 🔴 조회용 색인을 따로 만들지 않는다.
--    화면이 부르는 것은 "내가 저장한 것 전부" (user_id = ?) 인데, 위 UNIQUE 제약이 만드는
--    색인이 (user_id, place_id) 라 그 질의를 앞 칸으로 그대로 받는다.

COMMENT ON TABLE saved_place IS
    '사용자가 저장한(하트) 장소. 여행과 무관한 전역 목록이다. 여행별 후보 판단은 recommendation_place_action 이 따로 갖는다.';
