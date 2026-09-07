-- S15P21E201-666 — 안전 피처를 ESTIMATED 로 저장할 수 없게 막는다.
--
-- 🔴 지금까지 DB 는 **추정한 알레르기 정보를 받아 주고 있었다.**
--
-- 수집 명세 6.2 는 이렇게 못 박았고,
--   "알레르기·식단·접근성 정보가 없거나 검증되지 않은 장소를 안전하다고 표시하지 않는다"
-- S15P21E201-545 의 완료 기준 2 도 결측이 안전값으로 바뀌지 않는 것을 요구한다.
--
-- 그런데 place_feature(V20260904000000)에 실제로 걸린 제약은 둘뿐이었다.
--   ck_place_feature_evidence_status        VERIFIED | ESTIMATED | UNKNOWN 중 하나인가
--   ck_place_feature_unknown_has_no_value   UNKNOWN 이면 값이 비어 있는가
--
-- 어느 쪽도 evidence_status 를 feature_type 과 묶지 않는다. 그래서 아래가 통과했다.
--
--   INSERT INTO place_feature (..., feature_type, value, evidence_status, ...)
--   VALUES (..., 'ALLERGEN_TAG', '{"peanut": false}'::jsonb, 'ESTIMATED', ...);
--
-- 그 행이 들어오면 하드 필터(user_place_code_map 의 HARD_FILTER)가 그것을 근거로
-- 후보를 통과시킨다. **추측이 안전 판정이 된다.**
--
-- 🔴 지금 사고가 안 난 이유는 장소 피처를 채우는 코드가 아직 없기 때문이다
--    (place 패키지에 package-info.java 하나뿐이고, main/java 어디에도 place_feature
--    참조가 없다). 채우는 작업이 시작되면 그때는 늦다 — 잘못 들어온 행을 나중에
--    골라내려면 어느 것이 추측이었는지를 그 행 말고는 알 방법이 없다.
--
-- ── 왜 규칙이 아니라 제약인가 ─────────────────────────────────────────────────
--
-- "안전 피처는 추측하지 않는다" 를 문서와 코드 리뷰에 맡기면 언젠가 잊힌다.
-- 테스트도 마찬가지다 — 테스트는 있는 경로를 검사하고 새로 생긴 경로는 검사하지 않는다.
-- 값을 채우는 경로가 앞으로 여러 개 생긴다(수기 입력 · 외부 API · 텍스트 추출 ·
-- 사진 판독). 그 전부에 같은 규칙을 기억시키는 대신 **DB 가 한 번 거부하게 만든다.**
--
-- ── 무엇을 막지 않는가 ────────────────────────────────────────────────────────
--
-- 🔴 VERIFIED 와 UNKNOWN 은 그대로 받는다. 안전 피처를 저장 자체가 안 되게 만들면
--    "확인했다" 도 "모른다" 도 적을 수 없게 되고, 그건 명세가 요구하는 것의 반대다 —
--    UNKNOWN 은 지워야 할 상태가 아니라 **남겨야 할 사실**이다.
--    막는 것은 딱 하나, "추측했는데 값이 있다" 뿐이다.

ALTER TABLE place_feature
    ADD CONSTRAINT ck_place_feature_safety_never_estimated
        CHECK (feature_type NOT IN
                 ('ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG', 'STAIRS_PRESENT')
               OR evidence_status <> 'ESTIMATED');

COMMENT ON CONSTRAINT ck_place_feature_safety_never_estimated ON place_feature IS
    'S15P21E201-666 — 알레르기·식단·접근성·계단은 추정값을 저장할 수 없다. 수집 명세 6.2 · FR-REC-02. 🔴 VERIFIED·UNKNOWN 은 그대로 받는다 — 막는 것은 "추측했는데 값이 있다" 하나뿐이다.';

-- 🔴 이 네 종이 user_place_code_map 에서 HARD_FILTER · FLAG_COMPARE 로 쓰이는 것과
--    같은 목록이어야 한다 (V20260904020000). 목록이 갈리면 대조표에는 하드로 적혀
--    있는데 추정값이 들어올 수 있는 종류가 생기고, 그건 아무 오류도 내지 않는다.
--    그 일치는 PlaceFeatureCodeMapTest 가 검사한다.
