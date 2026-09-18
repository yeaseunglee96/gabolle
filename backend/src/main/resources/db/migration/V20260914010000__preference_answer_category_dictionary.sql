-- ══════════════════════════════════════════════════════════════════════════════
-- 취향 답의 CATEGORY 낱말을 사전으로 막는다 (S15P21E201-915)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- S15P21E201-904 가 **장소 쪽** 낱말을 사전(place_feature_code) + 외래키로 막았다.
-- 그런데 맞춰보는 일은 양쪽이 한다. 사용자가 고른 값에 사전에 없는 낱말이 있으면
-- 교집합은 여전히 0 이고, 증상은 오류가 아니라 「맞는 장소 없음」이다.
-- 🔴 한쪽만 막는 것으로는 904 가 찾아낸 사고를 못 막는다.
--
-- 이 표를 만든 V20260903120000 이 이미 이 자리를 예고했다:
--   "값 안쪽 코드는 화면 옵션과 장소 태그 온톨로지가 확정된 뒤 같은 코드로 고정한다
--    — 그래서 차원만 여기서 막고 value 안쪽은 막지 않는다."
-- **904 가 그 확정이었다. 이 마이그레이션이 그 약속을 지킨다.**


-- ── 🔴 왜 외래키가 아니라 방아쇠인가 ─────────────────────────────────────────
--
-- 904 는 장소 쪽에 「그 갈래일 때만 값이 차는 생성 칼럼 + 복합 외래키」를 썼다.
-- 여기서는 그 수법을 못 쓴다 — preference_answer.value 는 낱말이 하나가 아니라
-- **목록**이다 (["SEA_BEACH","FOOD"]).
--
--   · CHECK 는 다른 표를 쳐다볼 수 없다 (CHECK 안에 부질의 금지)
--   · 외래키는 칸 하나를 볼 뿐 배열 안 원소를 못 본다
--
-- 🔴 이 방식이 **못 하는 것**: 사전에서 낱말을 지우는 것은 안 막힌다. 외래키였다면
--    "쓰는 중" 이라고 막혔을 것이다. 사전은 지금 추가만 일어나므로 받아들인다.
--    지우기가 실제로 생기면 그때 목록을 한 줄씩 눕히는 방식으로 올린다.


-- ── 🔴 값 모양이 둘이다. 둘 다 받아야 한다 ───────────────────────────────────
--
-- S15P21E201-635 가 확인한 사실이고 PreferenceJson.parseCodes 가 그대로 구현하고 있다:
--
--   ["SEA_BEACH","FOOD"]            ← 배포된 앱이 실제로 보내는 모양
--   {"codes":["SEA_BEACH","FOOD"]}  ← 기존 시험과 다른 클라이언트가 쓰는 모양
--
-- 한쪽만 받으면 멀쩡한 저장을 거부한다. 배포된 앱은 즉시 못 고치고 이미 저장된 답도
-- 있다 — 읽는 쪽이 둘 다 받기로 한 것과 **같은 이유로** 막는 쪽도 둘 다 받는다.


-- ── 🔴 켜도 배포가 안 깨진다 — 그리고 그래서 남는 일이 있다 ──────────────────
--
-- 방아쇠는 **앞으로 들어오는 것**만 본다. 이미 들어가 있는 행은 다시 검사하지 않으므로
-- 이 마이그레이션은 운영에서 실패하지 않는다.
--
-- 🔴 그 대가로, 운영 preference_answer 의 CATEGORY 에 들어 있는 사전 밖 낱말
--    (2026-09-13 확인 기준 "ACTIVE" 2건)은 **그대로 남는다.** 그것을 어느 낱말로
--    읽을지는 사람이 판정할 일이라 여기서 건드리지 않는다 (S15P21E201-915 의 ② 항).
--    판정이 안 되면 그 답은 UNKNOWN(안 물어봤음)으로 내린다 — SKIPPED(보고 일부러
--    건너뜀)로 내리면 안 된다. 사용자는 건너뛴 적이 없다.


CREATE OR REPLACE FUNCTION ck_preference_answer_category_dictionary()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
    codes   JSONB;
    unknown TEXT;
BEGIN
    -- 값이 없는 경우는 여기서 판정하지 않는다. ck_preference_answer_value_matches_status
    -- 가 이미 그 규칙(고른 답에는 값이 있어야 한다)의 주인이다 — 두 곳이 같은 것을
    -- 막으면 어느 쪽이 거부했는지가 메시지마다 달라진다.
    IF NEW.dimension <> 'CATEGORY' OR NEW.answer_status <> 'SELECTED' OR NEW.value IS NULL THEN
        RETURN NEW;
    END IF;

    codes := CASE
        WHEN jsonb_typeof(NEW.value) = 'array' THEN NEW.value
        WHEN jsonb_typeof(NEW.value) = 'object' AND jsonb_typeof(NEW.value -> 'codes') = 'array'
            THEN NEW.value -> 'codes'
    END;

    -- 🔴 모양을 못 박지 않으면 배열이 아닌 값이 들어왔을 때 아래 풀기가 아무것도 안 보고
    --    조용히 지나간다. 지금 고치고 있는 것과 정확히 같은 종류의 사고다.
    IF codes IS NULL THEN
        RAISE EXCEPTION
            'CATEGORY 취향 답의 값이 낱말 목록이 아니다: % — ["SEA_BEACH"] 또는 {"codes":["SEA_BEACH"]} 여야 한다',
            NEW.value
            USING ERRCODE = 'check_violation';
    END IF;

    -- 🔴 낱말이 아닌 원소(숫자·null·객체)도 사전 밖으로 친다. 읽는 쪽은 그런 원소를 조용히
    --    버리지만, 버려진다는 것이 저장해도 된다는 뜻은 아니다 — DB 가 "이 사람은 이것을
    --    골랐다" 고 기록해 두면 나중에 세는 쪽은 그게 뜻 없는 값인 줄 모른다.
    SELECT string_agg(DISTINCT coalesce(e.elem #>> '{}', 'null'), ', ')
      INTO unknown
      FROM jsonb_array_elements(codes) AS e(elem)
     WHERE jsonb_typeof(e.elem) <> 'string'
        OR NOT EXISTS (
               SELECT 1
                 FROM place_feature_code c
                WHERE c.feature_type = 'CATEGORY_TAG'
                  AND c.feature_key = e.elem #>> '{}');

    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION
            'CATEGORY 취향 답에 사전에 없는 낱말이 있다: % — 사전은 place_feature_code (feature_type = CATEGORY_TAG) 이고, 낱말을 늘릴 때는 스키마가 아니라 그 표에 행을 넣는다',
            unknown
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION ck_preference_answer_category_dictionary() IS
    'S15P21E201-915 — 취향 답 CATEGORY 의 낱말이 place_feature_code(CATEGORY_TAG) 사전에 있는지 본다. 🔴 낱말을 더할 때는 이 함수가 아니라 그 사전에 행을 넣는다. 🔴 사전에서 낱말을 지우는 것은 이 방식이 못 막는다(외래키가 아니다).';

CREATE TRIGGER trg_preference_answer_category_dictionary
    BEFORE INSERT OR UPDATE ON preference_answer
    FOR EACH ROW
    EXECUTE FUNCTION ck_preference_answer_category_dictionary();


-- ── 이 마이그레이션이 **안 하는** 것 ─────────────────────────────────────────
--
-- 🔴 ATMOSPHERE · FOOD_PREFERENCE 는 안 막는다. 태그형이지만 **사전이 어디에도 없다**
--    — 자바 목록도 CHECK 도 없다. 904 가 같은 자리에서 같은 이유로 거절했다:
--    "그 낱말 목록을 여기서 지어내면 그 순간 저장소가 정한 사전이 되어 버리고, 실제로
--     쓰이던 값이 배포 때 거부된다. 모르는 것을 아는 척하지 않는다."
--    사전이 정해지면 이 함수의 첫 IF 에 그 차원을 더하면 된다.
--
-- 🔴 점수형 다섯(LOCALITY·QUIETNESS·TOURIST_PREFERENCE·SLOPE_PREFERENCE·SHADE_PREFERENCE)
--    은 사전 문제가 아니라 범위 문제다. 별개다.
--
-- 🔴 빈 목록([])은 막지 않는다. "골랐는데 아무것도 안 골랐다" 는 모순이라 막을 값이
--    있어 보이지만, 앱이 그 모양을 보내는지 확인된 바가 없다. 확인 없이 막으면 온보딩이
--    저장에 실패하고 사용자는 이유를 모른다. 확인되면 그때 한 줄 더한다.
