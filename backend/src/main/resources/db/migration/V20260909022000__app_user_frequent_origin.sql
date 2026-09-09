-- S15P21E201-265 — 회원이 자주 쓰는 출발지를 저장할 자리를 만든다.
--
-- 🔴 왜 trip 이 아니라 app_user 인가. trip.origin_lat/lng(V20260902_1·trip 표)는 "이번
-- 여행"의 출발지이고 여행마다 다르다. 여기서 필요한 것은 "이 회원이 반복해서 쓰는" 출발지
-- 다 — 이동시간 캐시가 매번 새 좌표라 못 맞는다는 것이 이유이므로(캐시 로직은 이 티켓
-- 몫이 아니다), 캐시 키가 안정적이려면 좌표가 회원 단위로 고정돼 있어야 한다.
--
-- app_user 는 이 저장소에서 유일하게 도메인 엔티티가 JPA 를 직접 쓰는 표다(AppUser.java) —
-- place/trip 처럼 domain/infra 를 나누지 않는 기존 패턴을 그대로 따른다. 새 표를 만들지
-- 않고 칼럼만 더하는 것도 그래서다 — 회원당 자주 쓰는 출발지는 지금은 하나면 충분하다
-- (여러 개를 저장해야 한다는 요구가 나오면 그때 별도 표로 옮긴다).
--
-- 좌표 쌍 제약은 place.ck_place_origin_pair·trip 의 origin 칼럼과 같은 모양이다 — 위도만
-- 있고 경도가 없는 반쪽 좌표가 들어오는 것을 막는다.
ALTER TABLE app_user
    ADD COLUMN frequent_origin_lat   DOUBLE PRECISION,
    ADD COLUMN frequent_origin_lng   DOUBLE PRECISION,
    ADD COLUMN frequent_origin_label VARCHAR(100),

    ADD CONSTRAINT ck_app_user_frequent_origin_pair
        CHECK ((frequent_origin_lat IS NULL) = (frequent_origin_lng IS NULL)),
    ADD CONSTRAINT ck_app_user_frequent_origin_range
        CHECK (frequent_origin_lat IS NULL
               OR (frequent_origin_lat BETWEEN -90 AND 90
                   AND frequent_origin_lng BETWEEN -180 AND 180));

COMMENT ON COLUMN app_user.frequent_origin_lat IS
    '회원이 반복해서 쓰는 출발지 위도 (S15P21E201-265). trip.origin_lat 과 다르다 — 이건 여행마다 바뀌지 않는, 회원에 고정된 값이다. 이동시간 캐시가 매번 새 좌표라 못 맞는 문제 때문에 필요해졌다. 이 칸을 실제로 읽는 캐시 로직은 이 티켓 범위가 아니다 — 저장 자리만 만든다.';
COMMENT ON COLUMN app_user.frequent_origin_lng IS
    '회원이 반복해서 쓰는 출발지 경도. lat 과 함께 있거나 함께 없다 (ck_app_user_frequent_origin_pair).';
COMMENT ON COLUMN app_user.frequent_origin_label IS
    '그 출발지를 부르는 이름(예: "집", "회사"). 화면 표시용 — 좌표만으로는 사용자가 무엇을 저장했는지 알 수 없다.';
