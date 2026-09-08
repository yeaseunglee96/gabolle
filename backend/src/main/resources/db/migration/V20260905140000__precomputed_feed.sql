-- S15P21E201-580 — 앱이 읽을 화면을 미리 만들어 두고, 읽을 때는 그 줄을 그대로 낸다.
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 바꾸는 것 하나: 계산이 일어나는 시점
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 지금까지의 모양은 "요청이 오면 그때 계산한다" 였다. 홈 화면을 열 때마다 온톨로지
-- (**장소·조건·관계를 기계가 읽게 적어 둔 사전**)를 훑고, 제약을 판정하고, 순위를
-- 매긴다. 사람이 화면을 여는 그 순간에.
--
-- 그 방식의 한계는 인스턴스(**온톨로지에 등록된 개별 사물 하나하나 — 장소 한 곳,
-- 노선 하나**)가 늘어날수록 **읽기가 같이 느려진다**는 것이다. 데이터를 잘 모을수록
-- 앱이 느려진다 — 노력이 벌을 받는 구조다.
--
-- 이 파일은 그 순서를 뒤집는다.
--
--   쓸 때(글이 올라올 때 · 취향이 바뀔 때 · 밤에 한 번)  → 무겁게 계산해서 줄로 저장
--   읽을 때(사람이 앱을 켤 때)                          → 저장된 줄을 그대로 낸다
--
-- 이 방식을 fan-out on write(**쓰기 시 미리 뿌려두기** — 내용이 생기는 순간 그것을
-- 볼 사람들의 목록에 미리 꽂아 넣는 것)라고 한다. 트위터·인스타그램의 피드가 이렇게
-- 돈다. 읽기는 `WHERE build_id = ? ORDER BY position` 한 번으로 끝나고, 조인이 없다.
--
-- 🔴 공짜가 아니다. 대가가 정확히 셋이고 아래에서 각각 어떻게 막는지 적는다.
--    (1) 저장 공간이 사용자 수만큼 곱해진다      → 세대 만료(expires_at)와 정리로 막는다
--    (2) 미리 만든 것은 낡는다                    → 세대에 재료의 판을 박아 두고 낡음을 알린다
--    (3) 다시 만드는 도중에 사람이 열면 반쪽을 본다 → 세대를 통째로 바꾼다 (아래 2절)
--
-- ══════════════════════════════════════════════════════════════════════════════
-- 이 파일이 만들지 않는 것
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 **피드를 실제로 채우는 계산은 여기 없다.** 표와 규칙만 만든다.
--    무엇을 어떤 순서로 넣을지는 추천 엔진(S15P21E201-543 이 자리를 만들어 둔
--    RecommendationEnginePort)이 붙어야 정해진다. 지어낸 점수로 채워 두면 그 값이
--    계약처럼 굳어서, 나중에 진짜 엔진이 붙을 때 무엇이 임시값이었는지 아무도 모른다.
--
-- 🔴 **커뮤니티 글 표(post)가 아직 없다.** 그래서 community_feed.post_id 에는
--    FK(**다른 표의 행을 가리키는 제약. 가리키는 행이 없으면 저장을 거부한다**)를
--    걸지 않는다. 없는 표를 가리키는 FK 는 만들 수 없고, 가짜 post 표를 여기서
--    만들면 커뮤니티 담당자와 주인이 둘이 된다. S15P21E201-543 이
--    preference_snapshot_id 를 같은 이유로 FK 없이 두었던 것과 같은 판단이다.
--    post 표가 생기면 ALTER TABLE 로 FK 만 더하면 되도록 이름을 맞춰 뒀다.
--
-- Flyway(**앱이 뜰 때 이 SQL 을 순서대로 한 번씩 실행해 DB 스키마를 맞추는 도구**)
-- 버전을 순번(V1, V2)이 아니라 UTC 시각으로 적는다 — 여러 티켓이 동시에 마이그레이션을
-- 올릴 때 순번은 반드시 충돌하지만 시각은 안 겹친다 (S15P21E201-543 이 정한 규칙).


-- ══════════════════════════════════════════════════════════════════════════════
-- 1. 사용자 취향 벡터 — 온톨로지를 매번 훑지 않으려고 미리 접어 둔 값
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 **이미 있는 preference_snapshot 과 다른 물건이다. 섞으면 안 된다.**
--
--   preference_snapshot / preference_answer (S15P21E201-554)
--     = **사람이 화면에서 고른 답 그대로.** "조용한 곳" 을 골랐다는 사실 자체.
--       고치지 않고 쌓는다. 있는 이유는 **재현** 이다 — 나중에 "그때 무엇을 보고
--       이렇게 추천했나" 에 답해야 하므로 원본이 그대로 남아야 한다.
--
--   user_taste_vector / user_taste_weight (이 파일)
--     = **그 답과 앱에서의 행동을 온톨로지 개념별 숫자로 바꾼 것.** 있는 이유는
--       **속도** 다. 이 값이 있으면 피드를 만들 때 온톨로지 관계를 다시 안 훑는다.
--
-- 원본을 지우고 이걸로 대신하는 것이 아니다. 둘 다 있어야 한다 — 하나는 왜를 위해,
-- 하나는 빠르기를 위해. 접은 값이 틀렸을 때 원본이 없으면 다시 접을 수가 없다.
--
-- 이 값들의 묶음을 부르는 일반적인 말이 **사용자 임베딩(user embedding)** 이다.
CREATE TABLE user_taste_vector (
    taste_vector_id  UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,

    -- 판 번호. UUID 에는 순서가 없어서 "이게 최신인가" 를 판정할 수 없으므로,
    -- 겹치지 않는 키(UUID)와 순서를 세는 값(정수)을 둘 다 둔다 — trip.version 과 같은 모양.
    version          INTEGER      NOT NULL,

    -- 🔴 어느 설문 답에서 출발했는가. 이 연결이 없으면 접은 값이 어디서 왔는지
    --    영영 못 되짚는다. 설문을 아직 안 받은 사용자(행동만으로 만든 벡터)는 NULL 이다.
    source_preference_snapshot_id UUID,

    -- 앱에서의 행동을 몇 건이나 반영했는가. 0 이면 순수하게 설문만으로 만든 벡터다.
    observed_event_count INTEGER  NOT NULL DEFAULT 0,
    -- 🔴 어느 시점까지의 행동을 반영했는가. 이게 없으면 다음 계산이 어디서부터
    --    이어 붙여야 하는지 몰라서, 같은 행동을 두 번 세거나 빠뜨린다.
    observed_until   TIMESTAMPTZ,

    -- 🔴 어떤 방식으로 접었는가. 접는 규칙이 바뀌면 같은 답에서 다른 숫자가 나오므로,
    --    이 값이 없으면 두 사용자의 벡터를 비교할 수 없다 — 같은 잣대인지 알 수 없다.
    vector_version   VARCHAR(100) NOT NULL,
    -- 어떤 온톨로지 판으로 접었는가. 개념이 추가·삭제되면 벡터의 뜻이 달라진다.
    ontology_version VARCHAR(100) NOT NULL,

    created_at       TIMESTAMPTZ  NOT NULL,
    -- 다음 판이 나오면 채운다. NULL 인 행이 현재 판이다.
    -- 🔴 지우지 않고 밀어낸다 — 과거 추천이 어느 벡터로 나왔는지 되짚어야 하기 때문이다.
    superseded_at    TIMESTAMPTZ,

    CONSTRAINT fk_user_taste_vector_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_taste_vector_preference_snapshot
        FOREIGN KEY (source_preference_snapshot_id)
        REFERENCES preference_snapshot (preference_snapshot_id),

    CONSTRAINT ck_user_taste_vector_version CHECK (version >= 1),
    CONSTRAINT ck_user_taste_vector_observed_count CHECK (observed_event_count >= 0),

    -- 🔴 행동을 반영했다면 "어디까지" 가 반드시 남는다. 이 줄이 없으면 이어 붙일
    --    기준점이 없는 벡터가 만들어지고, 그건 다음 계산에서 조용히 중복 반영된다.
    CONSTRAINT ck_user_taste_vector_observed_until
        CHECK (observed_event_count = 0 OR observed_until IS NOT NULL)
);

-- 🔴 **한 사람에게 현재 벡터는 하나뿐이다.** 조건이 붙은 UNIQUE 색인(partial index —
--    조건에 맞는 행들 사이에서만 중복을 막는 색인)으로 DB 가 직접 막는다.
--    애플리케이션이 실수로 두 개를 현재로 두면 어느 것으로 피드를 만들었는지
--    알 수 없게 되는데, 데이터는 멀쩡해 보인다.
CREATE UNIQUE INDEX uq_user_taste_vector_current
    ON user_taste_vector (user_id) WHERE superseded_at IS NULL;

CREATE UNIQUE INDEX uq_user_taste_vector_version
    ON user_taste_vector (user_id, version);

COMMENT ON TABLE user_taste_vector IS
    '사용자 취향을 온톨로지 개념별 숫자로 접어 둔 판. 원본 답은 preference_snapshot 에 그대로 남는다.';


-- 벡터의 성분 하나 = 한 행.
--
-- 🔴 JSONB 한 칸에 통째로 넣지 않은 이유가 있다. 차원 이름을 DB 가 검사할 수 있어야
--    하기 때문이다. 이 팀은 취향 차원 여덟 개를 이미 preference_answer 에서 CHECK 로
--    고정했는데, 벡터 쪽만 자유 문자열이면 'ATMOSPHERE' 와 'atmosphere' 가 같이 들어와
--    조용히 다른 것이 된다. 읽기 경로는 이 표를 안 지나가므로(피드가 이미 만들어져 있다)
--    행이 많아지는 대가는 읽기 성능에 안 온다.
CREATE TABLE user_taste_weight (
    taste_vector_id UUID             NOT NULL,

    -- preference_answer.dimension 과 **같은 목록**이어야 한다. 어긋나면 대조표
    -- (user_place_code_map)가 없는 코드를 가리킨다.
    dimension       VARCHAR(50)      NOT NULL,
    -- 그 차원 안의 값. 예: dimension='CATEGORY' 일 때 code='CAFE'.
    -- 🔴 코드 목록은 화면 옵션·장소 태그 온톨로지가 확정된 뒤 고정한다. 그전까지
    --    막지 않는다 — preference_answer 가 value 안쪽을 안 막은 것과 같은 이유다.
    code            VARCHAR(50)      NOT NULL,

    -- -1(싫다) ~ +1(좋다).
    weight          DOUBLE PRECISION NOT NULL,

    -- 이 숫자가 어디서 나왔는가. 설문만인가, 행동만인가, 둘을 섞었는가.
    evidence        VARCHAR(20)      NOT NULL,
    -- 이 값을 뒷받침한 관측 수. 1건으로 매긴 0.9 와 200건으로 매긴 0.9 는 다른 값이다.
    support         INTEGER          NOT NULL DEFAULT 0,

    updated_at      TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_user_taste_weight PRIMARY KEY (taste_vector_id, dimension, code),

    CONSTRAINT fk_user_taste_weight_vector
        FOREIGN KEY (taste_vector_id)
        REFERENCES user_taste_vector (taste_vector_id) ON DELETE CASCADE,

    CONSTRAINT ck_user_taste_weight_dimension
        CHECK (dimension IN ('CATEGORY', 'ATMOSPHERE', 'LOCALITY', 'QUIETNESS',
                             'TOURIST_PREFERENCE', 'FOOD_PREFERENCE',
                             'SLOPE_PREFERENCE', 'SHADE_PREFERENCE')),

    CONSTRAINT ck_user_taste_weight_range
        CHECK (weight >= -1.0 AND weight <= 1.0),

    CONSTRAINT ck_user_taste_weight_evidence
        CHECK (evidence IN ('SURVEY', 'INTERACTION', 'BLENDED')),

    CONSTRAINT ck_user_taste_weight_support CHECK (support >= 0),

    -- 🔴 행동에서 나왔다고 적었으면 뒷받침한 관측이 실제로 있어야 한다.
    --    support=0 인 INTERACTION 은 "행동을 봤다" 고 주장하면서 아무것도 안 본 것이다.
    CONSTRAINT ck_user_taste_weight_interaction_has_support
        CHECK (evidence = 'SURVEY' OR support > 0)
);

-- 🔴 **건너뛴 차원은 행을 만들지 않는다. 0 을 넣지 않는다.**
--
--    이 저장소는 이미 같은 원칙을 preference_answer 에서 DB 로 강제하고 있다
--    (ck_preference_answer_value_matches_status — 건너뜀·모름에는 값을 실을 수 없다).
--    벡터에서 그 원칙이 깨지기 가장 쉽다. 벡터는 "빈 칸을 0 으로 채우는" 것이 수학적으로
--    자연스러워 보이기 때문이다.
--
--    그런데 0 은 "싫지도 좋지도 않다" 라는 **의견**이고, 없음은 "안 물어봤다" 라는
--    **무지**다. 한 번 0 으로 적으면 둘을 영영 구분할 수 없고, 그때부터 추천은
--    "이 사람은 카페에 관심 없다" 를 근거로 카페를 빼기 시작한다 — 물어본 적도 없이.
COMMENT ON TABLE user_taste_weight IS
    '취향 벡터의 성분. 안 물어본 차원은 0 이 아니라 행 없음으로 표현한다.';


-- ══════════════════════════════════════════════════════════════════════════════
-- 2. 피드 세대 — 다시 만드는 동안 사람이 반쪽을 보지 않게 하는 장치
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 **이 표가 이 파일에서 가장 중요하다.** 미리 계산해 두는 방식의 진짜 어려움은
--    "언제 계산하나" 가 아니라 **"다시 계산하는 동안 어떻게 하나"** 이기 때문이다.
--
-- 순진한 방법은 이렇다: 옛 줄을 지우고 새 줄을 넣는다.
--   DELETE FROM user_feed WHERE user_id = ?;   ← 이 사이에 사람이 앱을 켜면
--   INSERT INTO user_feed ...;                    빈 화면이나 반쪽 화면을 본다
--
-- 그래서 **지우고 다시 쓰지 않는다.** 새 세대를 통째로 옆에 만들고, 다 만든 뒤에
-- "현재는 이것" 이라는 표시만 옮긴다. 옛 세대는 그 순간까지 멀쩡히 읽힌다.
-- 배포에서 쓰는 blue-green(**새 판을 옆에 다 띄운 뒤 접속만 옮기는 방식**)과 같다.
--
--   1) status='BUILDING' 으로 세대를 하나 연다
--   2) 그 세대에 줄을 다 넣는다        ← 이 동안 읽기는 옛 세대를 본다. 영향 0
--   3) 한 트랜잭션에서:
--        옛 READY → 'SUPERSEDED'
--        새 BUILDING → 'READY'
--   4) 옛 세대는 나중에 지운다 (줄은 ON DELETE CASCADE 로 같이 사라진다)
--
-- 3)에서 둘이 동시에 READY 가 되는 순간이 없어야 한다. 그것을 아래 조건부 UNIQUE
-- 색인이 **DB 에서** 막는다 — 애플리케이션의 조심성에 맡기지 않는다.
CREATE TABLE feed_build (
    build_id         UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,

    -- 어느 화면의 피드인가. HOME = 앱을 켰을 때, COMMUNITY = 커뮤니티에 들어갔을 때.
    surface          VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,

    -- ── 무엇으로 만들었는가 (낡음 판정의 근거) ──────────────────────────────
    --
    -- 🔴 이 값들이 이 표의 존재 이유의 절반이다. 미리 만든 피드는 반드시 낡는데,
    --    무엇으로 만들었는지 안 적어 두면 **낡았다는 사실 자체를 알 수 없다.**
    taste_vector_id        UUID,
    constraint_snapshot_id UUID,
    -- 추천 엔진을 통해 만들었다면 그 요청 번호. 사람이 "왜 이게 떴지" 를 물었을 때
    -- recommendation_candidate 까지 따라가 탈락한 후보들까지 볼 수 있다.
    source_request_id      UUID,

    model_version    VARCHAR(100),
    feature_version  VARCHAR(100),
    ontology_version VARCHAR(100),
    policy_version   VARCHAR(100),
    dataset_version  VARCHAR(100),
    service_version  VARCHAR(100),

    entry_count      INTEGER      NOT NULL DEFAULT 0,

    created_at       TIMESTAMPTZ  NOT NULL,
    ready_at         TIMESTAMPTZ,
    -- 이 세대를 언제까지 믿을 것인가. 지나면 낡은 것으로 보고 다시 만든다.
    -- 🔴 NULL 은 "만료 없음" 이 아니라 "아직 안 정했다" 다. READY 에는 반드시 있어야 한다.
    expires_at       TIMESTAMPTZ,

    failure_reason   VARCHAR(64),

    CONSTRAINT fk_feed_build_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_build_taste_vector
        FOREIGN KEY (taste_vector_id) REFERENCES user_taste_vector (taste_vector_id),
    CONSTRAINT fk_feed_build_constraint_snapshot
        FOREIGN KEY (constraint_snapshot_id)
        REFERENCES constraint_snapshot (constraint_snapshot_id),

    CONSTRAINT ck_feed_build_surface
        CHECK (surface IN ('HOME', 'COMMUNITY')),

    CONSTRAINT ck_feed_build_status
        CHECK (status IN ('BUILDING', 'READY', 'SUPERSEDED', 'FAILED')),

    CONSTRAINT ck_feed_build_entry_count CHECK (entry_count >= 0),

    -- 🔴 READY 인 세대는 "언제 준비됐고, 무엇으로 만들었고, 언제까지 믿는가" 가 전부 있어야 한다.
    --    S15P21E201-543 의 ck_recommendation_job_versions_present 와 같은 모양이고 같은 이유다 —
    --    버전을 못 구했다고 'unknown' 을 넣으면 그 피드가 어느 판에서 나왔는지 영영 모른다.
    CONSTRAINT ck_feed_build_ready_shape
        CHECK (status <> 'READY'
               OR (ready_at IS NOT NULL
                   AND expires_at IS NOT NULL
                   AND taste_vector_id IS NOT NULL
                   AND ontology_version IS NOT NULL
                   AND policy_version IS NOT NULL
                   AND dataset_version IS NOT NULL
                   AND service_version IS NOT NULL)),

    -- 🔴 홈 피드는 장소를 추천하므로 안전 제약(알레르기·휠체어)이 걸린다.
    --    무엇을 기준으로 걸렀는지 모르는 홈 피드는 READY 가 될 수 없다.
    --    커뮤니티 글에는 그 판정이 적용되지 않으므로 요구하지 않는다.
    CONSTRAINT ck_feed_build_home_needs_constraints
        CHECK (status <> 'READY' OR surface <> 'HOME' OR constraint_snapshot_id IS NOT NULL),

    -- 실패는 원인 없이 남지 않는다 (543 과 같은 원칙).
    CONSTRAINT ck_feed_build_failure_has_reason
        CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL)
);

-- 🔴 **원자적 교체를 실제로 강제하는 한 줄.**
--    한 사용자·한 화면에 READY 세대는 최대 하나다. 새 세대를 READY 로 올리려면
--    같은 트랜잭션에서 옛 세대를 먼저 내려야 한다 — 안 내리면 DB 가 거부한다.
--
--    조건 없는 UNIQUE 로는 못 만든다. SUPERSEDED 세대가 여러 개 쌓이는 것은
--    정상이기 때문이다. 조건부 UNIQUE 색인은 PostgreSQL 에 있고 MySQL 에는 없다 —
--    ERD 가 MySQL 기준이라 이 자리가 원래 없었다 (S15P21E201-554 가 같은 상황을 겪었다).
CREATE UNIQUE INDEX uq_feed_build_ready
    ON feed_build (user_id, surface) WHERE status = 'READY';

-- 만료된 세대를 찾아 다시 만드는 배치가 쓴다.
CREATE INDEX ix_feed_build_expiry
    ON feed_build (status, expires_at);
CREATE INDEX ix_feed_build_user_created
    ON feed_build (user_id, created_at DESC);

COMMENT ON TABLE feed_build IS
    '피드 한 세대. 다시 만들 때 지우지 않고 새 세대를 만든 뒤 READY 표시만 옮긴다.';
COMMENT ON COLUMN feed_build.expires_at IS
    'READY 세대는 반드시 만료 시각이 있다. NULL 은 만료 없음이 아니라 미정이다.';


-- ══════════════════════════════════════════════════════════════════════════════
-- 3. 홈 피드 줄 — 앱을 켰을 때 보이는 것
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE user_feed (
    build_id     UUID             NOT NULL,

    -- 화면에 보이는 순서. 0 부터. 점수로 다시 정렬하지 않는다 —
    -- 🔴 순서는 만들 때 이미 정해졌다. 읽을 때 정렬하면 그 순간 계산이 생기고,
    --    이 파일의 목적이 사라진다.
    position     INTEGER          NOT NULL,

    item_type    VARCHAR(30)      NOT NULL,
    item_id      UUID             NOT NULL,

    score        DOUBLE PRECISION,
    -- 왜 이것이 여기 있는가. 화면의 "이런 이유로 골랐어요" 가 이 값으로 만들어진다.
    reason_codes VARCHAR(64)[]    NOT NULL DEFAULT '{}',

    -- 🔴 **화면에 필요한 값의 사본.** 이름·사진·한 줄 설명처럼 카드 하나를 그리는 데
    --    필요한 것을 여기에 복사해 둔다. 이것이 "읽을 때 서버는 보여주기만 한다" 의
    --    실제 구현이다 — 조인이 0 회가 된다.
    --
    --    이렇게 사본을 두는 것을 비정규화(denormalization — 빠르게 읽으려고 같은 값을
    --    일부러 두 곳에 두는 것)라고 한다. 대가는 명확하다: **원본이 바뀌면 사본이 낡는다.**
    --    장소 이름이 바뀌어도 이 사본은 다음 세대가 만들어질 때까지 옛 이름을 보여준다.
    --    그 대가를 감수하는 이유는 피드가 원래 "지금 이 순간의 진실" 이 아니라
    --    "최근에 고른 추천" 이기 때문이다. 가격·영업시간처럼 틀리면 사람이 헛걸음하는
    --    값은 여기 넣지 않는다 — 그건 상세 화면에서 그때 조회한다.
    payload      JSONB            NOT NULL DEFAULT '{}'::jsonb,

    created_at   TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_user_feed PRIMARY KEY (build_id, position),

    CONSTRAINT fk_user_feed_build
        FOREIGN KEY (build_id) REFERENCES feed_build (build_id) ON DELETE CASCADE,

    -- 같은 세대에 같은 것이 두 번 뜨지 않는다.
    CONSTRAINT uq_user_feed_item UNIQUE (build_id, item_type, item_id),

    CONSTRAINT ck_user_feed_position CHECK (position >= 0),

    CONSTRAINT ck_user_feed_item_type
        CHECK (item_type IN ('PLACE', 'ITINERARY', 'COURSE')),

    CONSTRAINT ck_user_feed_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),

    -- 🔴 **이유 없는 추천은 저장할 수 없다.**
    --    이 저장소는 추천을 "왜 이것이 나왔고 왜 저것이 안 나왔는가" 로 설명할 수 있게
    --    만들어 왔다 (recommendation_candidate 가 탈락한 후보까지 남기는 이유).
    --    피드는 그 설명이 가장 쉽게 사라지는 자리다 — 이미 계산이 끝난 결과만 남기 때문에.
    --    빈 배열을 막으면 채우는 쪽이 이유를 만들 수밖에 없다.
    CONSTRAINT ck_user_feed_has_reason
        CHECK (cardinality(reason_codes) > 0)
);

COMMENT ON TABLE user_feed IS
    '앱을 켰을 때 보여 줄 줄. 읽기는 build_id 로 한 번 훑는 것이 전부다.';
COMMENT ON COLUMN user_feed.payload IS
    '카드를 그리는 데 필요한 값의 사본. 가격·영업시간처럼 틀리면 헛걸음하는 값은 넣지 않는다.';


-- ══════════════════════════════════════════════════════════════════════════════
-- 4. 커뮤니티 피드 줄 — 커뮤니티에 들어갔을 때 보이는 글
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 홈과 표를 나눈 이유: 가리키는 것이 다르다. 홈은 장소·일정이고 커뮤니티는 글·글쓴이다.
-- 한 표에 넣고 종류 칸으로 가르면, 어느 쪽에도 안 맞는 빈 칸이 절반씩 생긴다.
CREATE TABLE community_feed (
    build_id     UUID             NOT NULL,
    position     INTEGER          NOT NULL,

    -- 🔴 FK 없음 — post 표가 아직 없다 (이 파일 머리말). 표가 생기면 여기에
    --    ALTER TABLE ... ADD CONSTRAINT fk_community_feed_post 만 더하면 되도록
    --    이름을 post_id 로 맞춰 뒀다.
    post_id      UUID             NOT NULL,
    -- 글쓴이. 차단·숨김 처리에 쓰인다 — 읽을 때 글을 다시 조회하지 않고 거를 수 있어야 한다.
    author_id    UUID             NOT NULL,

    score        DOUBLE PRECISION,
    reason_codes VARCHAR(64)[]    NOT NULL DEFAULT '{}',
    payload      JSONB            NOT NULL DEFAULT '{}'::jsonb,

    created_at   TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_community_feed PRIMARY KEY (build_id, position),

    CONSTRAINT fk_community_feed_build
        FOREIGN KEY (build_id) REFERENCES feed_build (build_id) ON DELETE CASCADE,

    CONSTRAINT uq_community_feed_post UNIQUE (build_id, post_id),

    CONSTRAINT ck_community_feed_position CHECK (position >= 0),
    CONSTRAINT ck_community_feed_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_community_feed_has_reason
        CHECK (cardinality(reason_codes) > 0)
);

CREATE INDEX ix_community_feed_author ON community_feed (author_id);

COMMENT ON TABLE community_feed IS
    '커뮤니티에 들어갔을 때 보여 줄 글. post 표가 생기면 post_id 에 FK 를 더한다.';


-- ══════════════════════════════════════════════════════════════════════════════
-- 5. 이미 있는 추천 기록과 잇는다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- recommendation_job 은 "이 요청을 어떤 취향으로 계산했나" 를 preference_snapshot_id 로
-- 남긴다. 그 취향을 **접은 값**으로도 계산했다면 어느 벡터였는지 같이 남아야, 나중에
-- "접는 방식을 바꿨더니 추천이 달라졌다" 를 판정할 수 있다.
--
-- 🔴 NULL 을 허용한다. 벡터 없이(설문 원본만으로) 계산하는 경로가 지금도 유효하고,
--    앞으로도 대체 경로로 남는다.
ALTER TABLE recommendation_job
    ADD COLUMN taste_vector_id UUID;

ALTER TABLE recommendation_job
    ADD CONSTRAINT fk_recommendation_job_taste_vector
    FOREIGN KEY (taste_vector_id) REFERENCES user_taste_vector (taste_vector_id);

COMMENT ON COLUMN recommendation_job.taste_vector_id IS
    '이 추천을 접은 취향 벡터로 계산했다면 그 판. 설문 원본만 썼으면 NULL.';
