-- S15P21E201-456 — 숙소 지정과 여행 이용 조건(영어메뉴·해외카드·혼밥우선·최대환승) 저장.
--
-- 🔴 알레르기·식단은 새로 만들지 않는다. TripConstraint(type=ALLERGY/DIET, V120000)가
--    이미 코드화된 값(constraint_key)으로 저장하고 있고, 그 값으로 장소를 거르는 길도
--    이미 있다(PlaceCandidateQueryService 의 excludedFeatures). jsonb+GIN 요구사항은
--    "이 값으로 장소를 검색할 것인가" 를 먼저 묻는데, 이미 코드값 매칭(문자열 비교)으로
--    충족되고 있어 새로 jsonb 색인을 만들 필요가 없다 — 이 판단은 백엔드 조사 결과다.
--
-- 🔴 나머지 다섯(영어메뉴·해외카드·혼밥우선·숙소·최대환승)은 TripConstraint 로 보내지
--    않는다. TripConstraint 는 "위반하면 후보에서 제거"(HARD) 하는 안전 제약을 위해
--    answerStatus·evidenceStatus·scope 같은 무거운 구조를 갖췄는데, 이 다섯은
--    - "이 사람"이 아니라 "이번 여행"에 대한 값이고
--    - place_feature 에 아직 짝지을 자리가 없어 HARD 필터로 쓸 수 없는 선호(SOFT)다.
--    그래서 trip 표에 칼럼으로 둔다 — 과설계하지 않는다.
ALTER TABLE trip
    ADD COLUMN accommodation_place_id UUID,
    ADD COLUMN english_menu_required  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN foreign_card_required  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN solo_friendly_priority BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL 이면 제한 없음. 자차(PRIVATE_CAR) 이동이면 저장 시점(application 계층,
    -- TripCreationService)이 이 값을 무시하고 NULL 로 만든다 — 자차에는 환승 개념이
    -- 없다. travel_modes 는 배열 칸이라 DB CHECK 로 교차 검증하지 않는다.
    ADD COLUMN max_transit_transfers  INTEGER;

ALTER TABLE trip
    ADD CONSTRAINT fk_trip_accommodation_place
        FOREIGN KEY (accommodation_place_id) REFERENCES place (place_id),
    ADD CONSTRAINT ck_trip_max_transit_transfers
        CHECK (max_transit_transfers IS NULL OR max_transit_transfers >= 0);

COMMENT ON COLUMN trip.accommodation_place_id IS
    '매일 여기서 시작하고 여기로 돌아온다. place.category 가 숙소류(지금은 LODGING 하나, AccommodationCategories 참고)인 행을 가리키는 것이 기대값이지만, 적재된 숙소 자료가 아직 없어 DB CHECK 로 category 값까지는 강제하지 않는다.';
COMMENT ON COLUMN trip.max_transit_transfers IS
    '대중교통 최대 환승 횟수. travel_modes 에 PRIVATE_CAR 가 있으면 application 계층이 저장 전에 NULL 로 만든다.';
