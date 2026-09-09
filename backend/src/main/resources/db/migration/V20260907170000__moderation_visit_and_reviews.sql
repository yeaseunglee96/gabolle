-- 신고·검토 · 방문 인증 · 리뷰 · 실제 도착 시각
-- (S15P21E201-254 · -267 · -279 · -287 · -408 · -293)
--
-- 🔴 번호를 날짜로 지어내지 않았다. 머지 직전에 대상 브랜치의 최대 번호를 다시 확인한다 —
--    오늘 두 번 겹쳤고 한 번은 운영을 죽였다(INC-DEPLOY-003). 작업을 시작한 시점의 최대값은
--    답이 아니다. 이제 CI 잡(-703)이 대상 브랜치 tip 과 대조해 잡는다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. 신고와 검토 상태 (-254 · -267)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 기록을 "즉시 안 보이게" 만드는 방법이 둘 있었고 새 칸을 골랐다.
--
--    (가) deleted_at 을 찍는다 — 기존 조회가 전부 NULL 만 보므로 코드를 하나도 안 고쳐도
--         즉시 사라진다. 그런데 기각(-267)했을 때 되살릴 수가 없다. 삭제와 보류가 같은
--         칸이면 "지운 것" 과 "잠깐 감춘 것" 을 구분할 수 없고, 작성자에게도 삭제된 것으로
--         보인다.
--    (나) moderation_state 칸을 새로 둔다 — 되살릴 수 있고 삭제와 구분된다. 대가는 기존
--         조회 경로를 <b>전부</b> 고쳐야 하는 것이다. 하나라도 빠뜨리면 신고된 기록이 그
--         화면에만 계속 보인다.
--
--    (나)를 골랐다. 되살릴 수 없는 것은 기능이 아니고, 빠뜨림은 테스트로 막을 수 있다.
ALTER TABLE story
    ADD COLUMN moderation_state VARCHAR(20) NOT NULL DEFAULT 'VISIBLE';

ALTER TABLE story
    ADD CONSTRAINT ck_story_moderation_state
        CHECK (moderation_state IN ('VISIBLE', 'UNDER_REVIEW', 'REMOVED'));

COMMENT ON COLUMN story.moderation_state IS
    '신고 검토 상태. UNDER_REVIEW·REMOVED 는 피드·상세에서 빠진다. 🔴 deleted_at 과 다르다 — 이 칸은 기각(-267)으로 VISIBLE 로 되돌릴 수 있고, deleted_at 은 작성자가 지운 것이라 되돌리지 않는다.';

-- 피드 색인에 이 칸을 넣는다. 색인 조건과 조회 조건이 어긋나면 색인을 안 타고 표를 통째로
-- 훑는다 — 지금은 기록이 적어 안 보이지만 늘면 그때 느려지고 원인 찾기가 어렵다.
DROP INDEX IF EXISTS ix_story_feed;
CREATE INDEX ix_story_feed
    ON story (publish_at DESC, story_id DESC)
    WHERE deleted_at IS NULL AND moderation_state = 'VISIBLE';

CREATE TABLE story_report (
    story_report_id  UUID         PRIMARY KEY,
    story_id         UUID         NOT NULL,
    reporter_user_id UUID         NOT NULL,
    -- 개인정보 노출 / 불쾌한 내용 / 스팸 / 기타 (-254)
    reason           VARCHAR(30)  NOT NULL,
    -- 기타를 골랐을 때 적는 자유 입력. 없을 수 있다
    detail           VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 운영자가 처리한 시각과 결과. NULL 이면 아직 대기 중이다
    resolved_at      TIMESTAMPTZ,
    resolution       VARCHAR(20),
    resolved_by      UUID,

    CONSTRAINT fk_story_report_story    FOREIGN KEY (story_id) REFERENCES story (story_id) ON DELETE CASCADE,
    -- 🔴 신고자는 SET NULL 이 아니라 CASCADE 도 아니다 — 탈퇴한 사람의 신고도 검토 근거로
    --    남아야 하므로 사용자 삭제가 이 행을 지우거나 비우지 않게 <b>참조를 걸지 않는다.</b>
    --    대신 신고 수를 셀 때 중복만 막으면 되고, 신고자 이름은 검토 화면에 안 보여준다.
    CONSTRAINT ck_story_report_reason
        CHECK (reason IN ('PRIVACY', 'OFFENSIVE', 'SPAM', 'OTHER')),
    CONSTRAINT ck_story_report_resolution
        CHECK (resolution IS NULL OR resolution IN ('REMOVED', 'DISMISSED')),
    -- 처리 시각과 결과는 함께 있거나 함께 없다
    CONSTRAINT ck_story_report_resolution_pair
        CHECK ((resolved_at IS NULL) = (resolution IS NULL)),
    -- 🔴 같은 사람이 같은 기록을 두 번 신고해도 신고 수가 늘지 않는다 (-254 완료 기준).
    --    코드에서 세는 대신 표가 막는다 — 코드로 막으면 동시에 두 번 눌렀을 때 통과한다.
    CONSTRAINT uq_story_report_once
        UNIQUE (story_id, reporter_user_id)
);

-- 검토 목록은 "미처리 신고를 오래된 순" 으로 읽는다 (-267)
CREATE INDEX ix_story_report_pending
    ON story_report (created_at ASC)
    WHERE resolved_at IS NULL;

CREATE INDEX ix_story_report_story
    ON story_report (story_id);

COMMENT ON TABLE story_report IS
    '기록 신고 (S15P21E201-254·-267). 같은 사람의 중복 신고는 UNIQUE 가 막고, 같은 기록의 여러 신고는 검토 화면에서 하나로 묶어 보여준다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. 방문 인증 (-279)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 <b>좌표 칸이 없다.</b> 이것이 이 표의 가장 중요한 성질이고 티켓 완료 기준이 "인증 뒤
--    데이터베이스 어디에도 좌표 값이 없다" 다.
--
--    인증하려고 보낸 위치를 쌓으면 그 사람이 언제 어디 있었는지의 기록이 된다. 인증은
--    "가까이 있었다" 는 판정만 필요하고 그 판정은 요청 처리 중에 끝난다. 그래서 좌표는
--    받아서 쓰고 버린다 — 칸이 없는 것이 그 약속의 구현이다(story 표가 region 만 두고
--    좌표 칸을 안 둔 것과 같은 판단).
CREATE TABLE place_visit_verification (
    place_visit_verification_id UUID        PRIMARY KEY,
    place_id                    UUID        NOT NULL,
    user_id                     UUID        NOT NULL,
    verified_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 판정에 쓴 거리(미터). 좌표가 아니라 <b>결과</b>다 — 이 값만으로는 위치를 복원할 수 없다.
    -- 남기는 이유는 나중에 "인증 기준이 너무 느슨했나" 를 볼 수 있어야 하기 때문이다
    distance_m                  INTEGER     NOT NULL,

    CONSTRAINT fk_place_visit_verification_place
        FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE CASCADE,
    CONSTRAINT fk_place_visit_verification_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_place_visit_verification_distance
        CHECK (distance_m >= 0),
    -- 🔴 (user_id, place_id) 에 UNIQUE 를 걸지 않는다. 같은 곳을 다시 갈 수 있고, 그때마다
    --    인증이 남는 것이 맞다. "인증했는가" 판정은 존재 여부로 본다
    CONSTRAINT ck_place_visit_verification_time
        CHECK (verified_at IS NOT NULL)
);

CREATE INDEX ix_place_visit_verification_lookup
    ON place_visit_verification (user_id, place_id, verified_at DESC);

COMMENT ON TABLE place_visit_verification IS
    '위치로 확인한 방문 (S15P21E201-279). 🔴 좌표 칸이 없다 — 인증에 쓴 위치는 저장하지 않는다. 남는 것은 장소·사람·시각과 판정 거리뿐이다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 3. 장소 리뷰 (-287 · -408)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 위치 권한을 거부한 사람의 리뷰도 <b>받는다.</b> 대신 로컬 점수에 넣지 않는다.
--    거부하면 입을 막는 것이 되고, 그건 위치 권한을 사실상 강제하는 것이다 (-287 목적).
CREATE TABLE place_review (
    place_review_id UUID        PRIMARY KEY,
    place_id        UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    -- 네 가지 평가. 1~5. 안 매긴 항목은 NULL 이다 — 0 으로 두면 "최하점" 과 구분이 안 된다
    food_score          SMALLINT,
    price_score         SMALLINT,
    accessibility_score SMALLINT,
    onsite_score        SMALLINT,
    body            VARCHAR(1000),
    -- 🔴 인증 여부가 이 표의 핵심 칸이다. 로컬 점수 계산이 이 값으로 넣을 것과 뺄 것을 가른다
    verified        BOOLEAN     NOT NULL,
    -- 🔴 좌표가 아니라 <b>지역 단위 문자열</b>이다("해운대구"). 정확한 좌표가 시각과 함께
    --    쌓이면 그 사람의 하루 동선이 복원된다 (-408 작업 내용). story.region 과 같은 형태다
    region          VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT fk_place_review_place FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE CASCADE,
    CONSTRAINT fk_place_review_user  FOREIGN KEY (user_id)  REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_place_review_food          CHECK (food_score          IS NULL OR food_score          BETWEEN 1 AND 5),
    CONSTRAINT ck_place_review_price         CHECK (price_score         IS NULL OR price_score         BETWEEN 1 AND 5),
    CONSTRAINT ck_place_review_accessibility CHECK (accessibility_score IS NULL OR accessibility_score BETWEEN 1 AND 5),
    CONSTRAINT ck_place_review_onsite        CHECK (onsite_score        IS NULL OR onsite_score        BETWEEN 1 AND 5),
    -- 네 항목이 전부 비고 글도 없으면 아무 내용이 없는 리뷰다
    CONSTRAINT ck_place_review_has_content
        CHECK (food_score IS NOT NULL OR price_score IS NOT NULL OR accessibility_score IS NOT NULL
               OR onsite_score IS NOT NULL OR body IS NOT NULL),
    -- 한 사람이 한 장소에 리뷰 하나. 다시 쓰면 덮어쓴다
    CONSTRAINT uq_place_review_once UNIQUE (place_id, user_id)
);

-- 로컬 점수 계산이 "이 장소의 인증된 리뷰" 를 읽는다
CREATE INDEX ix_place_review_verified
    ON place_review (place_id, verified)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN place_review.verified IS
    '위치로 방문이 확인된 리뷰인가. 🔴 로컬 점수는 이 값이 참인 것만 센다. 거짓인 리뷰도 저장하고 화면에 보여준다 — 위치 권한을 거부한 사람의 입을 막지 않기 위해서다 (-287).';
COMMENT ON COLUMN place_review.region IS
    '지역 단위 문자열. 좌표는 방문 확인에만 쓰고 저장하지 않는다 — 이 표에 lat·lng 칸이 없는 것이 그 약속의 구현이다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- 4. 방문지 실제 도착·출발 시각 (-293)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 <b>itinerary_item 에 칸을 더하지 않는다.</b> 이 판단이 이 절의 핵심이다.
--
--    일정은 편집할 때마다 새 판을 만들고 항목을 전부 <b>복사</b>한다
--    (ItineraryRevision.copyItems). 그래서 itinerary_item 에 실제 도착 시각을 적으면
--    다음 편집에서 그 판의 항목만 남고 <b>실제로 다녀온 기록이 사라진다.</b> 사용자가
--    일정을 한 번 고치면 어제 찍은 도착 시각이 없어지는 것이다.
--
--    항목이 판을 건너 살아남는 이름은 item_key 다(그 값이 유지되는 것이
--    ItineraryRevision 주석에 명시돼 있다). 그래서 (itinerary_id, item_key) 로 키를 잡는다.
CREATE TABLE itinerary_item_actual (
    itinerary_item_actual_id UUID        PRIMARY KEY,
    itinerary_id             UUID        NOT NULL,
    -- 🔴 itinerary_item_id 가 아니라 item_key 다. 위 주석 참고
    item_key                 UUID        NOT NULL,
    arrived_at               TIMESTAMPTZ,
    departed_at              TIMESTAMPTZ,
    recorded_by              UUID        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_itinerary_item_actual_itinerary
        FOREIGN KEY (itinerary_id) REFERENCES itineraries (itinerary_id) ON DELETE CASCADE,
    -- 같은 방문지에 다시 보내면 덮어쓴다 (-293 완료 기준)
    CONSTRAINT uq_itinerary_item_actual UNIQUE (itinerary_id, item_key),
    -- 둘 다 있으면 출발이 도착보다 앞설 수 없다. 하나만 보내는 것은 허용한다
    CONSTRAINT ck_itinerary_item_actual_order
        CHECK (arrived_at IS NULL OR departed_at IS NULL OR departed_at >= arrived_at),
    -- 아무것도 없는 행을 만들지 않는다
    CONSTRAINT ck_itinerary_item_actual_has_value
        CHECK (arrived_at IS NOT NULL OR departed_at IS NOT NULL)
);

CREATE INDEX ix_itinerary_item_actual_itinerary
    ON itinerary_item_actual (itinerary_id);

COMMENT ON TABLE itinerary_item_actual IS
    '방문지의 실제 도착·출발 시각 (S15P21E201-293). 🔴 itinerary_item 이 아니라 (itinerary_id, item_key) 로 키를 잡는다 — 편집할 때마다 항목이 복사되므로 항목 행에 적으면 다음 편집에서 사라진다.';
