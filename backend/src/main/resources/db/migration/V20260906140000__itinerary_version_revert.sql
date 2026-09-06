-- S15P21E201-284 — 일정 되돌리기(REVERT)가 판에 남길 자리를 만든다.
--
-- 되돌리기는 별도 스냅샷 표를 두지 않는다. 일정은 이미 덮어쓰지 않는 판(version) 체인이라
-- "편집 직전 상태"가 그 판(baseVersion 이 되돌아갈 대상으로 가리키는 옛 판) 그대로
-- itinerary_versions·itinerary_item·itinerary_leg·itinerary_excluded_place 에 남아 있다.
-- 되돌리기는 그 옛 판의 내용을 새 판으로 복사하는 것뿐이다(ItineraryRevision.copyOf).
--
-- 🔴 어느 판으로 돌아갔는지는 base_version 이 아니라 새 칸 reverted_from_version 에 적는다.
--    base_version 은 "되돌리기를 누를 때 보고 있던 최신 판"이라 되돌아간 대상과는 대개
--    다른 값이다(예: 5번 판을 보다가 "2번으로 되돌리기"를 누르면 새 판은 version=6,
--    base_version=5, reverted_from_version=2 — 셋 다 다른 값이다). 이 둘을 하나로
--    합치면 "무엇을 보고 있었는가"와 "무엇으로 돌아갔는가"가 구분되지 않는다.

-- ══════════════════════════════════════════════════════════════════════════════
-- ck_itinerary_version_operation — REVERT 를 더한다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 NOT VALID 를 안 쓴다. 이 제약은 값을 하나 더하는 것뿐이라(REMOVE 가 아니라 ADD)
--    기존 행이 담고 있던 값(CREATE·REGENERATE·...)은 새 목록에도 전부 들어 있다 —
--    즉 기존 행 중 이 제약을 위반할 행이 하나도 없다. NOT VALID 는 "기존 행이 위반할
--    제약을 조일 때" 쓰는 것이지(V20260905120000·V20260906120000 이 그런 경우다),
--    이렇게 위반 가능성 자체가 없는 변경까지 미룰 이유가 없다.
ALTER TABLE itinerary_versions DROP CONSTRAINT ck_itinerary_version_operation;

ALTER TABLE itinerary_versions ADD CONSTRAINT ck_itinerary_version_operation
    CHECK (operation IN ('CREATE', 'REGENERATE', 'REGENERATE_DAY', 'REPLACE_ITEM',
                         'REMOVE_ITEM', 'LOCK_ITEM', 'REORDER', 'REVERT'));

-- ══════════════════════════════════════════════════════════════════════════════
-- reverted_from_version — 되돌리기가 어느 판으로 돌아갔는가
-- ══════════════════════════════════════════════════════════════════════════════
ALTER TABLE itinerary_versions ADD COLUMN reverted_from_version INTEGER;

-- 기존 행은 전부 operation <> 'REVERT' 이고 이 칸이 NULL(방금 ADD COLUMN 한 칸의 기본값)
-- 이므로, 아래 CHECK 는 기존 행을 하나도 위반하지 않는다 — 여기도 NOT VALID 가 필요 없다.
ALTER TABLE itinerary_versions ADD CONSTRAINT ck_itinerary_version_reverted_from
    CHECK ((operation = 'REVERT') = (reverted_from_version IS NOT NULL)
           AND (reverted_from_version IS NULL OR reverted_from_version < version));

COMMENT ON COLUMN itinerary_versions.base_version IS
    '되돌리기를 누를 때 보고 있던 최신 판. reverted_from_version(되돌아간 대상 판)과는
    대개 다른 값이다 — 5번 판을 보다가 2번으로 되돌리면 base_version=5,
    reverted_from_version=2 다.';

COMMENT ON COLUMN itinerary_versions.reverted_from_version IS
    '되돌리기(operation=REVERT)가 내용을 복사해 온 옛 판. REVERT 가 아니면 NULL —
    ck_itinerary_version_reverted_from 이 이 대응을 지킨다. base_version(누를 때 보던
    최신 판)과는 다른 칸이다 — 그 주석 참고.';
