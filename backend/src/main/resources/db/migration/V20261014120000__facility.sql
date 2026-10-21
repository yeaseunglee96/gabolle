-- S15P21E201-2052 — 근처 편의시설: 주차장 · 약국 · 공중화장실 · 지하철 물품보관함.
--
-- 여행자가 내 위치(또는 고른 목적지) 근처의 ① 주차장(저렴한 순) ② 약국(지금 연 곳·일요일/공휴일·심야)
-- ③ 공중화장실(장애인용·기저귀 교환대) ④ 지하철 물품보관함을 찾는다. 자료는 공공 자료를 모아 만든 NDJSON
-- (한 줄에 JSON 하나인 글 파일) 넷이고, 적재기(FacilityLoaderRunner)가 이 표에 넣는다.
--
-- 🔴 장소(place) 표에 섞지 않는다. 장소 표는 추천·탐색·일정이 읽는 후보 목록이라, 주차장·화장실이 거기 들어가면
--    「오늘의 추천 코스」에 공중화장실이 끼어든다. 편의시설은 「지금 내 근처에 무엇이 있나」에만 쓰는 별도 자료다.
--    place_feature(장소에 붙는 사실 한 줄)도 쓰지 않는다 — 이 자료에는 붙을 장소가 없다.
--
-- 🔴 종류마다 칸이 달라(주차장은 요금표·시간표, 약국은 전화·요일별 시간, 화장실은 장애인용·기저귀 교환대, 보관함은 크기별
--    칸 수) 한 표에 칸을 다 늘어놓으면 대부분이 비어 있다. 공통 칸(이름·주소·좌표)만 일반 칸으로 두고 종류별 칸은
--    detail(JSONB — 안쪽 값으로 검색·색인이 되는 JSON 타입)에 둔다. detail 안쪽은 적재기가 값을 다듬어 넣는다
--    (예: 요금이 0원이거나 정수 최댓값이면 「모름」이라 null 로 비운다) — 모양은 FacilityReader 설명에 있다.
--
-- 🔴 좌표 근처 조회는 PostGIS 를 쓰지 않는다 (docs/DB-STANDARD.md 4절 — 확장을 켜면 설치·권한·백업 절차가 늘어난다).
--    장소 표(ix_place_coords)와 같은 방식이다: (종류, 위도, 경도) 색인으로 경계상자(반경을 감싸는 네모)를 좁히고, 실제 거리는
--    자바에서 다시 잰다(GeoDistance). 이 표는 전부 합쳐 5천 행 안쪽이라 충분하다.
--    PostGIS 로 넘어갈 신호는 장소 표와 같다 — 반경 질의가 느려지거나 행이 10만을 넘을 때.
--
-- 🔴 같은 (종류, 원천 번호)는 한 행이다. 적재기를 다시 돌리면 이 유일 제약(uq_facility_kind_source)을 따라 갱신되고
--    행이 늘지 않는다.

CREATE TABLE facility (
    facility_id     UUID             NOT NULL,
    -- PARKING · PHARMACY · TOILET · LOCKER. 새 종류는 이 목록과 자바 FacilityKind 를 함께 고친다.
    kind            VARCHAR(20)      NOT NULL,
    -- 원천 파일의 sourceId. 종류마다 번호 체계가 달라 (kind, source_id) 쌍이 유일하다.
    source_id       VARCHAR(200)     NOT NULL,
    name            VARCHAR(200)     NOT NULL,
    address         VARCHAR(300),
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    detail          JSONB            NOT NULL DEFAULT '{}'::jsonb,
    -- 어느 수집분에서 온 행인가 (예: facilities-20261014). 되돌릴 때 이 값으로 가른다.
    dataset_version VARCHAR(100)     NOT NULL,
    collected_at    TIMESTAMPTZ      NOT NULL,
    CONSTRAINT pk_facility PRIMARY KEY (facility_id),
    CONSTRAINT uq_facility_kind_source UNIQUE (kind, source_id),
    CONSTRAINT ck_facility_kind CHECK (kind IN ('PARKING', 'PHARMACY', 'TOILET', 'LOCKER')),
    -- 위도와 경도를 뒤바꿔 적은 행이 조용히 들어가지 않게 한다. 부산 상자는 적재기가 따로 검사한다.
    CONSTRAINT ck_facility_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_facility_lng CHECK (lng BETWEEN -180 AND 180),
    -- detail 은 늘 JSON 객체다. 배열이나 글자 하나가 들어오면 자바가 읽다 터진다.
    CONSTRAINT ck_facility_detail_object CHECK (jsonb_typeof(detail) = 'object')
);

-- 근처 조회가 훑는 모양: WHERE kind = ? AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?
-- 🔴 진짜 2차원 색인이 아니다 — 앞 칸(kind, lat)으로 좁히고 lng 는 그 안에서 거른다. 위아래로 긴 상자에서는 덜 효과적이지만
--    종류당 많아야 수천 행이라 문제가 되지 않는다.
CREATE INDEX ix_facility_kind_coords
    ON facility (kind, lat, lng);

COMMENT ON TABLE facility IS
    'S15P21E201-2052 — 근처 편의시설(주차장·약국·공중화장실·지하철 물품보관함). 장소(place)와 섞지 않는 별도 자료이고 추천·탐색에 쓰지 않는다.';
COMMENT ON COLUMN facility.detail IS
    '종류별 칸(JSON 객체). 요금·시간처럼 원천이 비었거나 이상한 값(0원·정수 최댓값·빈 시간)은 null 로 비워 둔다 — 모른다는 것을 값으로 지어내지 않는다.';
COMMENT ON COLUMN facility.dataset_version IS
    '이 행을 마지막으로 넣은 수집분. 같은 파일을 다시 돌리면 갱신된다. 새 수집분에서 사라진 행은 지우지 않고 남긴다.';
