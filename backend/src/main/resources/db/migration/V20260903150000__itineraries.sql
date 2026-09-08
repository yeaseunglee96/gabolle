-- S15P21E201-313 — 일정 편집 충돌 방지(ITN-03)를 지탱하는 표 둘을 PostgreSQL 에 만든다.
--
-- 지금까지 InMemoryItineraryRepository 로 409 동작을 재현했다. 서버를 끄면 편집
-- 이력이 사라진다. 이 파일이 그 뒤에 실제로 놓일 표를 만든다.
--
-- 🔴 db/migration 은 고지혁 님(S15P21E201-554)이 반납하며 이 자리를 넘겼다
--    (2026-09-03 쪽지 "itineraries 시작하셔도 됩니다"). 새 버전은 그쪽이 이미 올린
--    V20260903120000(-554)·V20260903123000(-352-event-outbox-join-axes) 보다 뒤로 잡았다.
--
-- 🔴 2026-09-03 06:2x 정정 — 버전을 V20260903130000 에서 V20260903150000 으로 올렸다.
--    고지혁 님의 -546(V20260903140000)이 먼저 배포됐는데, 그 배포 시점의 back/dev 에는
--    이 파일이 없었다. 이 파일이 나중에 머지되면서 "이미 적용된 140000 보다 낮은 130000
--    이 뒤늦게 나타난" 상태가 됐고, Flyway 의 기본 validate 가 그걸 거부해 배포가 죽었다
--    ("Detected resolved migration not applied to database: 20260903130000").
--
--    130000 은 어느 DB 에도 적용된 적이 없었다(고지혁 님이 flyway_schema_history 를
--    실측해 확인) — 그래서 이름만 올리면 Flyway 는 이걸 그냥 새 마이그레이션 하나로
--    본다. 이력 손질도 out-of-order 설정도 필요 없다.
--
-- 🔴 이 파일은 표만 만든다. 엔티티(자바 클래스)는 만들지 않는다 — S15P21E201-554 가
--    trip 표에서 쓴 것과 같은 순서다. InMemoryItineraryRepository 를 갈아 끼우는 것은
--    별도 커밋으로 뒤따른다.
--
-- 🔴 itinerary_items(장소·구간) 표는 이번에 안 만든다. 지금 도메인 코드
--    (Itinerary · ItineraryVersion)에 항목 목록이라는 개념 자체가 없다 —
--    ITN-03(항목 고정)이 항목을 저장하는 것이 아니라 "판 번호가 최신인가" 만
--    판정하기 때문이다. 실제 일정 항목·구간은 추천 계산이 붙는 티켓(S15P21E201-192
--    계열)에서 그 모양이 확정된 뒤 만든다. 지금 만들면 아무도 안 쓰는 표가 된다.

-- ── 일정 — 최신 판을 가리키는 포인터 ────────────────────────────────────────────
--
-- 🔴 latest_version 이 409 판정의 기준값이다(Itinerary.assertEditableFrom).
--    DEFAULT 를 안 둔다 — 이 표에는 아직 "일정을 새로 만드는" 경로가 없다
--    (ItineraryEditService.edit 은 기존 행을 찾아야만 동작한다. 없으면
--    NoSuchElementException). 그 경로가 생기는 티켓이 최초 값을 명시적으로 넣어야
--    하고, 지어낸 기본값을 두면 그 결정을 여기서 대신 내리는 셈이 된다.
CREATE TABLE itineraries (
    itinerary_id    UUID        PRIMARY KEY,
    trip_id         UUID        NOT NULL,
    latest_version  INTEGER     NOT NULL,

    -- 다른 모든 표가 갖는 기본 감사 칸. Itinerary(도메인 클래스)는 아직 이 값을
    -- 안 들고 있다 — 필요해지면 그때 게터를 추가한다. 표에는 지금 둔다.
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_itinerary_trip
        FOREIGN KEY (trip_id) REFERENCES trip (trip_id),

    CONSTRAINT ck_itinerary_latest_version CHECK (latest_version >= 1)
);

CREATE INDEX ix_itinerary_trip ON itineraries (trip_id);

COMMENT ON COLUMN itineraries.latest_version IS
    '409 판정의 기준값(Itinerary.assertEditableFrom). itinerary_versions 에 실제로 있는 가장 큰 version 과 같아야 한다 — 어긋나면 편집이 저장은 됐는데 포인터가 안 옮겨진 것이다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 일정의 판 — 덮어쓰지 않는 스냅샷 (ITN-03 · F-COL-02)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 이 표가 공동 편집 전체의 핵심이다. 모든 편집은 기존 판을 고치는 것이 아니라
--    새 판을 insert 한다. UNIQUE (itinerary_id, version) 이 마지막 방어선이다 —
--    응용 계층의 확인(baseVersion == latestVersion)은 확인과 저장 사이에 다른
--    요청이 끼어들 수 있어서(경쟁 조건) 그것만으로는 부족하다.
CREATE TABLE itinerary_versions (
    itinerary_version_id UUID        PRIMARY KEY,
    itinerary_id         UUID        NOT NULL,

    version              INTEGER     NOT NULL,
    -- 최초 생성만 없다(Operation.requiresBaseVersion). 그 외 모든 편집은 반드시
    -- "무엇을 보고 있었는지" 를 실어야 baseVersion != latestVersion 판정이 성립한다.
    base_version         INTEGER,

    operation            VARCHAR(20) NOT NULL,
    created_by           UUID        NOT NULL,

    -- 🔴 UUID 가 아니라 VARCHAR 다 — recommendation_job.request_id 와 다르다.
    --    사용자 편집(예: 항목 고정)은 추천 요청에서 나온 것이 아니라 진짜 request_id 가
    --    없다. ItineraryEditController 가 편집마다 "req_edit_<uuid>" 형태로 새로
    --    만드는데, 이건 UUID 형식이 아니고 recommendation_job 을 가리키지도 않는다 —
    --    API-07 이 "요청을 남겨야 한다" 를 요구해서 채우는 자리이지, 노출↔행동을
    --    잇는 진짜 축이 아니다(그 한계는 컨트롤러 주석에 이미 적혀 있다). UUID 로
    --    두면 저장 시점에 형식 오류로 깨진다.
    request_id           VARCHAR(64) NOT NULL,

    -- 🔴 다섯 다 필요하다. 하나만 없어도 재현이 깨진다(S15P21E201-542 3장).
    --    M1 은 재계산을 아직 안 붙였으므로 전부 NULL 로 들어온다 — 지어낸 값을
    --    넣지 않는다(ItineraryEditController.placeholderVersions 주석과 같은 이유).
    model_version        VARCHAR(100),
    feature_version      VARCHAR(100),
    ontology_version     VARCHAR(100),
    policy_version       VARCHAR(100),
    dataset_version      VARCHAR(100),

    created_at           TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_itinerary_version_itinerary
        FOREIGN KEY (itinerary_id) REFERENCES itineraries (itinerary_id),
    CONSTRAINT fk_itinerary_version_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (user_id),

    -- 🔴 이 저장소 전체에서 가장 중요한 제약이다. 두 번째 INSERT 가 여기서
    --    물리적으로 실패하고, 그 실패를 애플리케이션이 409 로 바꿔 준다
    --    (StaleItineraryVersionException). 응용 계층의 사전 확인은 이걸 보조할 뿐이다.
    CONSTRAINT uq_itinerary_version UNIQUE (itinerary_id, version),

    CONSTRAINT ck_itinerary_version_version CHECK (version >= 1),
    CONSTRAINT ck_itinerary_version_base_lt_version
        CHECK (base_version IS NULL OR base_version < version),

    CONSTRAINT ck_itinerary_version_operation
        CHECK (operation IN ('CREATE', 'REGENERATE', 'REGENERATE_DAY', 'REPLACE_ITEM',
                             'REMOVE_ITEM', 'LOCK_ITEM', 'REORDER')),

    -- Operation.requiresBaseVersion() — 최초 생성만 baseVersion 없이 통과한다.
    CONSTRAINT ck_itinerary_version_requires_base
        CHECK (operation = 'CREATE' OR base_version IS NOT NULL)
);

-- 한 일정의 이력을 최신순으로 읽는 조회. UNIQUE 색인(uq_itinerary_version)이
-- (itinerary_id, version) 을 이미 커버하지만 이건 최신순 정렬이 목적이라 별도로 둔다.
CREATE INDEX ix_itinerary_version_itinerary
    ON itinerary_versions (itinerary_id, version DESC);

COMMENT ON COLUMN itinerary_versions.request_id IS
    'API-07 이 요구해서 채우는 자리. UUID 가 아니다 — 사용자 편집에는 연결할 진짜 추천 요청이 없어 컨트롤러가 편집마다 새로 만든다. recommendation_job.request_id 와 조인되지 않는다.';
