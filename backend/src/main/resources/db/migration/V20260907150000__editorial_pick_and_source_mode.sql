-- S15P21E201-555 — Editor's Pick 발행본. 콜드스타트와 추천 실패 때 돌려줄 기준선.
--
-- 지금은 취향을 전부 건너뛴 계정이나 추천 엔진이 죽은 요청에 **빈 화면이 나간다.**
-- RecommendationService 는 반환할 후보가 없으면 422 NO_FEASIBLE_RESULT 를 던지고
-- (그것은 옳다 — 하드 제약을 풀어 목록을 채우지 않는다), 엔진이 없으면
-- ENGINE_NOT_CONFIGURED 로 실패한다. 둘 다 "보여줄 것이 없다" 로 끝난다.
--
-- 그 자리에 사람이 고른 목록을 둔다. FR-REC-03 · FR-REC-11 · FR-REC-15.
--
-- ── 왜 표가 둘인가 ───────────────────────────────────────────────────────────
--
-- 발행본(editorial_pick)과 그 안의 장소 순서(editorial_pick_place)를 나눈다.
-- 한 표에 장소 목록을 배열로 넣으면 place 를 참조하는 외래키를 걸 수 없고, 그러면
-- 삭제된 장소가 Pick 안에 남는다 — 그 Pick 은 발행돼 있는데 열면 빈 칸이 생긴다.
--
-- ── 🔴 contentVersion — 왜 행을 고치지 않고 새로 만드나 ──────────────────────
--
-- Pick 의 내용을 고칠 때 **같은 행을 갱신하지 않는다.** (pick_key, content_version)
-- 으로 새 행을 만들고 앞의 판은 그대로 둔다.
--
-- 3일 전에 어떤 사용자에게 나간 추천 결과에는 pick_id 가 기록돼 있다. 그 행을 나중에
-- 고쳐 버리면 **그때 무엇을 보여줬는지 아는 방법이 없어진다.** "왜 이걸 추천했나" 를
-- 되짚는 것이 이 저장의 목적 절반이므로, 그 절반이 사라지면 나머지도 의미가 없다.
--
-- 그래서 이 표는 추가만 한다(append-only). 낡은 판은 status 로 물러나고 지워지지 않는다.
--
-- ── 🔴 여기 적는 경고와 요청할 때 붙는 경고는 다른 것이다 ────────────────────
--
-- warning_codes 에 적는 것은 **발행 시점에 이미 아는 편집자의 경고**다.
--   "이 코스에는 계단이 있습니다" · "경사가 있어 유아차가 어렵습니다"
--
-- 요청할 때 붙는 경고는 다르다. 그것은 **그 사용자의 제약**과 대조해 그 순간 계산된다
--   (BaselineCandidateScorer 가 ALLERGEN_TAG · ACCESSIBILITY_TAG 등을 보고 만든다).
--
-- 이 둘을 한 칸에 섞으면, 사용자마다 달라야 하는 값이 모두에게 같게 나가거나 반대로
-- 편집자가 적어 둔 주의사항이 사용자에 따라 사라진다. **섞이면 아무 오류도 안 난다.**
-- 그래서 여기 있는 것은 사용자와 무관한 것만이고, 사용자별 판정은 저장하지 않는다.

CREATE TABLE editorial_pick (
    pick_id          UUID         PRIMARY KEY,

    -- 사람이 부르는 이름. 내용을 고쳐 새 판을 내도 이 값은 그대로다 —
    -- "부산 첫 방문 코스" 는 3판이 나와도 같은 Pick 이다.
    pick_key         VARCHAR(100) NOT NULL,

    -- 1 부터. 같은 pick_key 안에서만 의미가 있다 (위 설명 참고).
    content_version  INTEGER      NOT NULL,

    -- GLOBAL = 누구에게나 보여줄 수 있는 대표 Pick. 신규 계정과 fallback 이 이것을 받는다.
    -- LOCAL  = 지역·성향이 맞는 사람에게만 의미가 있는 Pick. locality_code 가 있어야 한다.
    scope            VARCHAR(20)  NOT NULL,

    status           VARCHAR(20)  NOT NULL,

    -- 🔴 제목은 두 언어 다 필수다. 하나를 NULL 로 허용하면 영어 사용자에게 한국어 제목이
    --    그대로 나가거나 빈 칸이 나가는데, **둘 다 오류를 내지 않아** 아무도 모른다.
    --    번역이 아직 없으면 발행하지 않는 것이 맞다 — 그것이 status=DRAFT 다.
    title_ko         VARCHAR(200) NOT NULL,
    title_en         VARCHAR(200) NOT NULL,

    -- 설명은 없어도 된다. 제목만으로 성립하는 Pick 이 있다.
    description_ko   TEXT,
    description_en   TEXT,

    -- LOCAL Pick 이 어느 지역인가. GLOBAL 이면 NULL 이다.
    locality_code    VARCHAR(50),

    -- 여러 Pick 을 나란히 보여줄 때의 순서. 작은 값이 앞이다.
    display_order    INTEGER      NOT NULL,

    -- 🔴 발행 시점에 아는 편집자의 경고만 (위 설명 참고). 없으면 빈 배열이고 NULL 이 아니다 —
    --    "경고가 없다" 와 "경고를 안 적었다" 를 가르는 것은 이 표의 일이 아니다.
    warning_codes    TEXT[]       NOT NULL DEFAULT '{}',

    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_editorial_pick_content_version CHECK (content_version >= 1),
    CONSTRAINT ck_editorial_pick_display_order   CHECK (display_order >= 0),
    CONSTRAINT ck_editorial_pick_scope           CHECK (scope IN ('GLOBAL', 'LOCAL')),
    CONSTRAINT ck_editorial_pick_status          CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),

    -- 🔴 LOCAL 인데 어느 지역인지 없으면 그 Pick 은 아무에게도 고를 수 없다. 반대로
    --    GLOBAL 에 지역이 붙어 있으면 "전체 대상" 과 "그 지역 대상" 중 무엇인지가 갈린다.
    --    두 경우 다 조용히 잘못 뜨므로 스키마에서 막는다.
    CONSTRAINT ck_editorial_pick_scope_locality
        CHECK ((scope = 'LOCAL') = (locality_code IS NOT NULL)),

    -- 발행됐다면 발행 시각이 있어야 한다. 그 반대도 같다 — 시각만 있고 상태가 DRAFT 인
    -- 행은 "발행했다가 되돌린 것" 인지 "실수" 인지 구분되지 않는다.
    CONSTRAINT ck_editorial_pick_published_at
        CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

-- 같은 이름의 같은 판은 하나뿐이다.
CREATE UNIQUE INDEX uq_editorial_pick_key_version
    ON editorial_pick (pick_key, content_version);

-- 🔴 한 pick_key 에서 **동시에 발행된 판은 하나뿐이다.** 부분 유니크 인덱스로 막는다.
--    막지 않으면 같은 Pick 의 2판과 3판이 동시에 PUBLISHED 로 남고, 어느 것을 보여줄지는
--    질의의 정렬 순서가 정한다 — 즉 배포마다 달라질 수 있고 아무 오류도 안 난다.
CREATE UNIQUE INDEX uq_editorial_pick_published
    ON editorial_pick (pick_key)
    WHERE status = 'PUBLISHED';

-- 기준선을 고르는 질의가 쓰는 길: 발행된 것 중 scope 로 걸러 display_order 로 정렬.
CREATE INDEX ix_editorial_pick_published_scope
    ON editorial_pick (scope, display_order)
    WHERE status = 'PUBLISHED';

COMMENT ON TABLE editorial_pick IS
    'S15P21E201-555 — Editor''s Pick 발행본. 콜드스타트·fallback 기준선. 🔴 추가만 한다(append-only) — 내용을 고칠 때는 같은 pick_key 의 새 content_version 을 만들고 앞 판은 RETIRED 로 물러난다. 이미 나간 추천 결과가 그 판을 가리키고 있어서다.';
COMMENT ON COLUMN editorial_pick.content_version IS
    '같은 pick_key 안에서 1 부터. 🔴 이 값이 있어야 "3일 전 그 사용자에게 무엇을 보여줬나" 를 되짚을 수 있다.';
COMMENT ON COLUMN editorial_pick.warning_codes IS
    '🔴 발행 시점에 아는 편집자의 경고만 (계단 있음 · 경사 있음 등). 사용자의 제약과 대조해 나오는 경고는 여기 넣지 않는다 — 그것은 요청마다 BaselineCandidateScorer 가 계산해 recommendation_candidate.warning_codes 에 남는다. 섞으면 사용자마다 달라야 할 값이 모두에게 같게 나가고, 아무 오류도 안 난다.';
COMMENT ON COLUMN editorial_pick.scope IS
    'GLOBAL = 누구에게나. 신규 계정과 fallback 이 이것을 받는다. LOCAL = locality_code 가 맞는 사람에게만.';

-- ── Pick 안의 장소와 그 순서 ────────────────────────────────────────────────
--
-- 🔴 순서(pick_rank)가 이 표의 존재 이유다. Editor's Pick 은 "좋은 곳 모음" 이 아니라
--    **사람이 정한 순서**다 — 아침에 갈 곳과 저녁에 갈 곳이 바뀌면 다른 코스가 된다.
--    그래서 순위는 추천 엔진이 매기지 않고 여기 적힌 것을 그대로 쓴다.

CREATE TABLE editorial_pick_place (
    pick_id    UUID    NOT NULL,
    place_id   UUID    NOT NULL,

    -- 1 부터. 이 값이 그대로 추천 결과의 final_rank 가 된다.
    pick_rank  INTEGER NOT NULL,

    -- 이 장소를 왜 골랐는지 편집자가 적는 한 줄. 결과의 추천 이유로 나갈 수 있다.
    note_ko    TEXT,
    note_en    TEXT,

    CONSTRAINT pk_editorial_pick_place PRIMARY KEY (pick_id, place_id),

    CONSTRAINT fk_editorial_pick_place_pick
        FOREIGN KEY (pick_id) REFERENCES editorial_pick (pick_id) ON DELETE CASCADE,

    -- 🔴 place 를 실제로 참조한다. 배열 컬럼으로 뒀다면 삭제된 장소가 Pick 안에 남고,
    --    발행된 Pick 을 열었을 때 빈 칸이 생긴다.
    CONSTRAINT fk_editorial_pick_place_place
        FOREIGN KEY (place_id) REFERENCES place (place_id),

    CONSTRAINT ck_editorial_pick_place_rank CHECK (pick_rank >= 1)
);

-- 🔴 한 Pick 안에서 같은 순위가 둘일 수 없다. 없으면 "1위가 두 개" 인 코스가 발행되고,
--    무엇을 먼저 보여줄지는 질의 정렬이 정한다.
CREATE UNIQUE INDEX uq_editorial_pick_place_rank
    ON editorial_pick_place (pick_id, pick_rank);

COMMENT ON TABLE editorial_pick_place IS
    'S15P21E201-555 — Pick 안의 장소와 사람이 정한 순서. 🔴 pick_rank 를 추천 엔진이 다시 매기지 않는다 — 이 순서가 코스 자체다.';
COMMENT ON COLUMN editorial_pick_place.pick_rank IS
    '1 부터. 그대로 recommendation_candidate.final_rank 가 된다.';

-- ── 🔴 씨앗 데이터가 여기 없는 이유 ─────────────────────────────────────────
--
-- S15P21E201-555 의 작업 내용 첫 줄은 "부산 대표·로컬 Editor's Pick 을 최소 3개
-- 발행 가능한 데이터로 준비한다" 이고, 그것은 **이 마이그레이션에서 못 한다.**
--
-- editorial_pick_place.place_id 가 place 를 참조하는데, 이 저장소에 place 행을 넣는
-- 코드나 씨앗이 **하나도 없다**(마이그레이션 전체에서 실제 INSERT 가 있는 표는
-- user_place_code_map 뿐이다). 참조할 장소가 없으면 Pick 을 발행할 수 없다.
--
-- 여기서 해운대·광안리 같은 장소를 직접 INSERT 하지 않는다. place 에는 place_id
-- 말고 유일 키가 없어서(V20260904000000), 나중에 수집 파이프라인이 같은 장소를
-- 넣으면 **같은 곳이 두 행으로 남고 무엇이 정본인지 아무도 모른다.** 그 중복은
-- 되돌리기가 특히 어렵다 — 어느 쪽을 지워야 하는지가 행 안에 안 적혀 있다.
--
-- 그래서 발행은 장소 데이터가 들어온 뒤의 **데이터 작업**으로 남긴다. 이 마이그레이션은
-- 그것을 받을 자리와 규칙까지만 만든다.

-- ════════════════════════════════════════════════════════════════════════════
-- 기록 쪽 — Pick 으로 만든 결과를 개인화 추천과 구분해 남긴다
-- ════════════════════════════════════════════════════════════════════════════
--
-- S15P21E201-555 의 완료 기준 셋 중 하나가 "글로벌 Pick 과 개인화 추천을 분석에서
-- 구분할 수 있다" 이고, 그것을 위해 두 가지가 필요하다.
--
--   1. fallback_mode 가 EDITORIAL_PICK 을 받을 수 있어야 한다 (지금은 CHECK 가 막는다)
--   2. source_mode 칸이 있어야 한다 (왜 둘 다인지는 아래)
--
-- ── 🔴 왜 fallback_mode 하나로는 안 되나 ────────────────────────────────────
--
-- 두 칸이 서로 다른 질문에 답한다.
--
--   source_mode    **무엇을** 보여줬나   개인화 추천인가, 편집자가 고른 목록인가
--   fallback_mode  **왜** 그것을 보여줬나 모델이 매겼나, 규칙이 매겼나, 대체했나
--
-- 이 둘이 갈리는 경우가 실제로 있다. 신규 계정에게 Pick 을 보여주는 것은 **정상 경로**다 —
-- 아무것도 실패하지 않았고, 그 사용자에 대해 아는 것이 없으니 그것이 맞는 답이다. 반면
-- 엔진이 죽어서 Pick 을 보여준 것은 **사고**다.
--
-- 화면에 나간 것은 둘 다 같은 Pick 이지만 뒤에서 봐야 하는 숫자는 정반대다. 앞의 것이
-- 늘어나는 것은 사용자가 늘었다는 뜻이고, 뒤의 것이 늘어나는 것은 서비스가 고장났다는
-- 뜻이다. **한 칸에 섞으면 장애율을 잴 수 없다.**

ALTER TABLE recommendation_job
    DROP CONSTRAINT ck_recommendation_job_fallback_mode;
ALTER TABLE recommendation_job
    ADD CONSTRAINT ck_recommendation_job_fallback_mode
        CHECK (fallback_mode IS NULL
               OR fallback_mode IN ('MODEL', 'RULE', 'BASELINE', 'EDITORIAL_PICK'));

ALTER TABLE recommendation_candidate
    DROP CONSTRAINT ck_recommendation_candidate_fallback_mode;
ALTER TABLE recommendation_candidate
    ADD CONSTRAINT ck_recommendation_candidate_fallback_mode
        CHECK (fallback_mode IS NULL
               OR fallback_mode IN ('MODEL', 'RULE', 'BASELINE', 'EDITORIAL_PICK'));

-- ── source_mode ─────────────────────────────────────────────────────────────
--
-- 🔴 NOT NULL 인데 기존 행이 있다. 기본값을 'PERSONALIZED' 로 두고 채운다.
--
-- **이것은 추측이 아니다.** Editor's Pick 은 이 마이그레이션이 생기기 전에는 존재하지
-- 않았으므로, 지금 있는 모든 행은 개인화 경로로 만들어진 것이 **증명된다.** 결측을
-- 낙관적으로 채우는 것과 다르다 — 여기서는 다른 값이 있었을 가능성 자체가 없다.
--
-- 🔴 채운 뒤에도 DEFAULT 를 남겨 둔다. 무엇을 얻고 무엇을 잃는지 적어 둔다.
--
--   얻는 것  앞으로 생기는 기록 경로가 이 칸을 잊어도 **맞는 값**이 들어간다.
--            Pick 이 아닌 모든 경로에서 PERSONALIZED 가 참이기 때문이다.
--   잃는 것  칸을 잊었다는 사실이 드러나지 않는다. 값이 틀리지는 않지만,
--            "이 경로가 이 칸을 의식하고 있는가" 는 알 수 없게 된다.
--
-- DEFAULT 를 빼고 NOT NULL 만 두는 쪽도 검토했다. 그러면 잊은 경로가 INSERT 에서 바로
-- 터져 드러난다. 하지만 이 칸을 잊는 경로는 **정의상 Pick 이 아닌 경로**이고, 그쪽을
-- 런타임에 죽이는 대가로 얻는 것이 "의식하고 있었는지 알 수 있다" 뿐이다. Pick 경로는
-- 값을 명시적으로 넣고(그러지 않으면 Pick 이 개인화로 집계되므로 그쪽이 진짜 위험이다),
-- 그것은 코드 리뷰와 이 주석이 지킨다.
ALTER TABLE recommendation_job
    ADD COLUMN source_mode VARCHAR(20) NOT NULL DEFAULT 'PERSONALIZED';
ALTER TABLE recommendation_job
    ADD CONSTRAINT ck_recommendation_job_source_mode
        CHECK (source_mode IN ('PERSONALIZED', 'EDITORIAL_PICK'));

ALTER TABLE recommendation_candidate
    ADD COLUMN source_mode VARCHAR(20) NOT NULL DEFAULT 'PERSONALIZED';
ALTER TABLE recommendation_candidate
    ADD CONSTRAINT ck_recommendation_candidate_source_mode
        CHECK (source_mode IN ('PERSONALIZED', 'EDITORIAL_PICK'));

-- 어느 Pick 의 어느 판을 보여줬는가. 개인화 추천이면 NULL 이다.
--
-- 🔴 pick_id 하나만 남긴다. editorial_pick 이 (pick_key, content_version) 으로 판마다
--    다른 행이라서, pick_id 만 있으면 이름과 판이 함께 따라온다 — 세 칸을 복사해 둘
--    필요가 없고, 복사해 두면 나중에 서로 어긋난다.
--
-- 🔴 외래키를 걸지 않는다. recommendation_job 은 **일어난 일의 기록**이고, 참조하는
--    Pick 이 나중에 어떻게 되든 그 기록은 남아야 한다. 외래키를 걸면 Pick 을 지우려 할 때
--    기록이 그것을 막거나(RESTRICT) 기록이 함께 지워진다(CASCADE) — 둘 다 틀렸다.
--    editorial_pick 이 append-only 이므로 실제로 지워질 일은 없지만, 그 약속을 이 표가
--    의존해야 하는 관계로 만들지는 않는다.
ALTER TABLE recommendation_job
    ADD COLUMN editorial_pick_id UUID;

COMMENT ON COLUMN recommendation_job.source_mode IS
    'S15P21E201-555 — PERSONALIZED | EDITORIAL_PICK. 🔴 fallback_mode 와 다른 질문에 답한다: 이 칸은 "무엇을 보여줬나", fallback_mode 는 "왜 그것을 보여줬나". 신규 계정에게 Pick 을 준 것은 정상 경로이고 엔진이 죽어서 준 것은 사고다 — 한 칸에 섞으면 장애율을 잴 수 없다.';
COMMENT ON COLUMN recommendation_job.editorial_pick_id IS
    'S15P21E201-555 — 어느 Pick 의 어느 판이었나. 개인화 추천이면 NULL. 🔴 외래키를 걸지 않는다 — 이 표는 일어난 일의 기록이고, 참조 대상의 수명에 묶이면 안 된다.';
COMMENT ON COLUMN recommendation_candidate.source_mode IS
    'S15P21E201-555 — PERSONALIZED | EDITORIAL_PICK. recommendation_job.source_mode 와 같은 값이 후보 행마다 내려온다 — 후보만 보고도 집계할 수 있어야 한다.';

-- 분석 질의가 쓰는 길: Pick 으로 나간 요청만 골라 세기.
CREATE INDEX ix_recommendation_job_source_mode
    ON recommendation_job (source_mode, created_at);
