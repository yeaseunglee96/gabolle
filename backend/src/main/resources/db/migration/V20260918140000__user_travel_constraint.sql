-- ══════════════════════════════════════════════════════════════════════════════
-- 여행 조건 모달의 답 — S15P21E201-1231
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 로그인 후 홈 첫 진입에 뜨는 「여행 조건」 모달이 답을 담을 자리가 없다.
-- 담는 것은 알레르기·식단·최대 보행거리·경사 피하기·계단 피하기·그늘길 우선이다.
--
-- ── 🔴 안 담는 것 셋 ─────────────────────────────────────────────────────────
--
-- 휠체어·유아차·큰 짐은 여기 안 담는다. 그 셋은 **여행마다 다르다** — 이번엔
-- 아이와 가고 다음엔 혼자 간다. 계정 기본값에 넣으면 한 번 켠 사람이 매 여행마다
-- 끄게 된다. 그 셋은 여행 만들기 질문으로 간다.
--
-- ── 🔴 상태가 넷이다. 그중 셋만 이 표에 있다 ────────────────────────────────
--
--   (행 없음)  한 번도 안 물어봤다   → 홈 첫 진입에 모달
--   LATER      「나중에」를 눌렀다   → 🔴 「일정 물어보기」를 누를 때마다 다시 묻는다
--   NEVER      「다시 묻지 않기」    → 더 안 묻는다. 마이페이지에서만 고친다
--   SAVED      「저장하고 시작」     → 더 안 묻는다
--
-- 「안 물어봄」에 행을 만들지 않는 이유는, 그것이 **아직 아무 일도 안 일어난 상태**라서다.
-- 행을 만들면 가입하는 모든 사람마다 빈 행이 하나씩 생기고, 그 행은 아무것도 안 알려 준다.
--
-- 🔴 LATER 와 NEVER 를 하나로 합치지 않는다. 「나중에 다시 물어봐 달라」와 「영영 묻지
--    마라」는 다른 답이고, 둘을 틀리면 사용자가 **끄고 싶은 모달을 매번 보거나 켜고 싶은
--    모달을 영영 못 본다.**
--
-- ── 🔴 씀씀이(preference_answer)에 차원 하나를 더하지 않은 이유 ─────────────
--
-- PUT /api/v1/me/preferences/spend 와 모양이 닮아서 그 표에 얹는 안을 먼저 봤다.
-- 안 했다. 이유 셋이고 전부 그 표의 제약에서 나온다.
--
--   (1) 그 표는 CHECK (answer_status IN ('SELECTED','SKIPPED','UNKNOWN')) 다.
--       여기에 LATER·NEVER·SAVED 를 더하면 **다른 모든 차원도 그 값을 받게 된다** —
--       씀씀이 답이 LATER 인 것은 아무 뜻이 없는데 DB 가 못 막는다
--   (2) 그 표는 CHECK ((answer_status='SELECTED') = (value IS NOT NULL)) 로 짝을
--       강제한다. 이쪽은 **SAVED 일 때만 값이 있다** — 같은 규칙을 두 이름으로 적게 된다
--   (3) LATER 에는 **행동이 붙어 있다**(다음에 또 물어본다). 한 표에 두면 그 규칙이
--       자기와 상관없는 차원들 위에 얹힌다
--
-- story_view 와 story_link_copy 를 나눌 때와 같은 판단이다 — **규칙이 갈릴 수 있으면
-- 합쳐 둔 표가 한쪽 규칙을 다른 쪽에 강요한다.**
--
-- ── 🔴 탈퇴 ──────────────────────────────────────────────────────────────────
--
-- app_user 에 ON DELETE CASCADE 를 건다. 그런데 AccountDeletionService 는 계정 행을
-- 지우지 않고 익명화하므로 그 CASCADE 는 한 번도 안 터진다. 그래서 이 표를
-- USER_OWNED_ROWS 에 **처음부터** 넣는다 — S15P21E201-1157 이 표 열 개를 뒤늦게 메운
-- 자리이고, 같은 일을 반복하지 않는다.
--
-- 🔴 AccountDeletionTableInventoryTest 가 이 마이그레이션 때문에 빨개진다. 의도한
--    것이다 — 그 검사는 「app_user 를 가리키는 표가 새로 생겼으니 탈퇴 때 어떻게 할지
--    정하라」고 묻는다. 이 MR 이 그 답과 함께 목록도 고친다.

CREATE TABLE user_travel_constraint (
    -- 🔴 사람당 한 줄이다. 그래서 user_id 가 곧 기본키다 — 따로 id 칸을 두면
    --    「같은 사람의 답이 둘」이 들어갈 자리가 생기고, 그때 어느 것이 최신인지를
    --    응용이 판단해야 한다. 판단할 일이 없게 만드는 쪽이 낫다.
    user_id     UUID        PRIMARY KEY,

    status      VARCHAR(10) NOT NULL,

    -- 🔴 칸으로 쪼개지 않는다. 항목이 늘 때마다 마이그레이션을 해야 하고, 이 값들은
    --    서버가 하나씩 조회할 대상이 아니라 추천 엔진에 통째로 넘기는 것이다.
    value       JSONB,

    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_user_travel_constraint_user FOREIGN KEY (user_id)
        REFERENCES app_user (user_id) ON DELETE CASCADE,

    CONSTRAINT ck_user_travel_constraint_status
        CHECK (status IN ('SAVED', 'LATER', 'NEVER')),

    -- 🔴 「값이 있다」와 「저장했다」가 어긋나지 못하게 DB 가 묶는다. 나중에·다시 묻지
    --    않기에 값이 붙어 있으면 그 값이 어디서 왔는지 아무도 설명할 수 없고,
    --    저장했는데 값이 없으면 화면이 빈 모달을 「저장됨」으로 그린다.
    CONSTRAINT ck_user_travel_constraint_value_matches_status
        CHECK ((status = 'SAVED') = (value IS NOT NULL))
);

COMMENT ON TABLE user_travel_constraint IS
    '여행 조건 모달의 답. 사람당 한 줄이고, 행이 없으면 「한 번도 안 물어봄」이다 (S15P21E201-1231)';
COMMENT ON COLUMN user_travel_constraint.status IS
    'SAVED(저장하고 시작) · LATER(나중에 — 일정 물어보기마다 다시 묻는다) · NEVER(다시 묻지 않기)';
COMMENT ON COLUMN user_travel_constraint.value IS
    '알레르기·식단·최대 보행거리·경사/계단 피하기·그늘길 우선. 휠체어·유아차·큰 짐은 여기 없다 — 여행마다 달라서 여행 만들기 질문으로 갔다';

-- ── 되돌리기 ─────────────────────────────────────────────────────────────────
--
-- DROP TABLE user_travel_constraint;
--
-- 🔴 되돌리면 사람들이 적어 둔 알레르기·식단이 사라진다. 다시 만들 수 없고,
--    모달이 모두에게 다시 뜬다(행이 없으면 「안 물어봄」이므로).
