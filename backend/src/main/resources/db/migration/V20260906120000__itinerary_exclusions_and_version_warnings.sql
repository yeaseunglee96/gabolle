-- S15P21E201-249 — 장소 제외 목록(판에 매달림)과 판 수준 경고 코드, 편집 Job 의 결과 포인터 CHECK.
--
-- 🔴 왜 판(itinerary_version_id)에 매다는가.
--    일정은 덮어쓰지 않는 판(version) 체인이고, itinerary_item·itinerary_leg 의 부모가
--    itinerary_id 가 아니라 itinerary_version_id 다(V20260905120000). 제외 목록도 같은
--    이유로 판에 매단다 — 판을 새로 만들 때마다(재계산·고정 등 어떤 편집이든) 바탕 판의
--    제외 목록이 그대로 새 판으로 복사되므로, "한 번 뺀 장소가 재계산을 몇 번 해도 다시
--    나오지 않는다" 가 항목·구간과 똑같은 하나의 복사 기계장치로 보장된다. 그리고
--    되돌리기(옛 판을 다시 최신으로 만드는 것)도 그 판이 갖고 있던 제외까지 함께
--    되돌린다 — 별도의 되돌리기 규칙이 필요 없다.
--
-- 🔴 왜 warning_codes 가 itinerary_item 이 아니라 itinerary_versions 에 있는가.
--    "이 날짜에는 대체할 장소가 없어 빈 시간대로 남았다" 같은 경고는 항목이 아예 없는
--    시간대에 대한 것이다. itinerary_item 은 항목이 있어야만 존재하는 행이라, 항목이
--    없는 상황을 항목 행에 적을 자리가 없다 — 그래서 판 전체에 적는다.
--
-- 🔴 왜 recommendation_job 의 CHECK 를 넓히는가.
--    ck_recommendation_job_result_present(V20260905120000)는 지금까지 job_type =
--    ITINERARY_GENERATION 만 봤다. 그런데 장소 제외(ITEM_REMOVE)·재계산
--    (ITINERARY_RECALCULATE) Job 도 SUCCEEDED 로 끝나면 그 결과가 어느 판인지
--    itinerary_id·itinerary_version 에 남아야 한다 — 아니면 "제외는 됐다는데 어느 판인지
--    아무 데도 안 남은" 상태가 생긴다. 그래서 그 둘도 같은 CHECK 안에 넣는다.
--
-- 🔴 왜 NOT VALID 인가. 이 표에는 이 CHECK 가 생기기 전에 만들어진 행이 이미 있고, 그
--    행들은 지금 만드는 조건을 몰랐으므로 걸릴 수 있다 — V20260905120000 이 같은 이유로
--    남긴 선례이자 경고다("NOT VALID 없이 걸면 마이그레이션이 그 행에 걸려 실패하고,
--    Flyway 가 멈추면 컨테이너가 기동하지 못한다. 이 저장소는 그 실수로 운영이 두 번
--    죽었다"). NOT VALID 는 "검사를 안 한다" 가 아니라 "지금 있는 행은 안 본다" 다 —
--    앞으로의 INSERT·UPDATE 는 전부 검사한다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 장소 제외 목록 — 판에 매달리는 스냅샷
-- ══════════════════════════════════════════════════════════════════════════════
CREATE TABLE itinerary_excluded_place (
    itinerary_excluded_place_id UUID         PRIMARY KEY,
    itinerary_version_id        UUID         NOT NULL,
    place_id                    UUID         NOT NULL,

    -- 항목이었다면 어느 항목이었나(itinerary_item.item_key). 재계산이 "후보에서 아예
    -- 빼라" 는 뜻으로만 제외를 걸었을 뿐 실제 항목으로 배치된 적이 없었다면 NULL 이다.
    item_key                    UUID,

    excluded_by                 UUID         NOT NULL,
    -- USER_REMOVED 등 — 왜 뺐는지의 코드다. 값은 ItineraryExclusion(도메인) 상수를 본다.
    reason_code                 VARCHAR(64)  NOT NULL,

    -- 🔴 사용자가 직접 입력한 자유 텍스트다. 이벤트 payload(Outbox)에 싣지 않는다 —
    --    사용자가 쓴 문장이 분석 이벤트에 그대로 남으면 개인정보·민감 정보가 실릴 수
    --    있다(RecommendationService 가 SensitiveDataInPayloadException 으로 이미 경계하는
    --    것과 같은 종류의 위험이다). DB 에는 남기되 이벤트로는 내보내지 않는다.
    operational_reason          VARCHAR(200),

    created_at                  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_itinerary_excluded_version FOREIGN KEY (itinerary_version_id)
        REFERENCES itinerary_versions (itinerary_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_excluded_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    CONSTRAINT fk_itinerary_excluded_by FOREIGN KEY (excluded_by) REFERENCES app_user (user_id),

    -- 같은 판 안에서 같은 장소를 두 번 제외 목록에 넣을 수 없다 — 이미 뺀 장소를
    -- 또 빼는 요청은 새 정보가 아니다.
    CONSTRAINT uq_itinerary_excluded UNIQUE (itinerary_version_id, place_id)
);

CREATE INDEX ix_itinerary_excluded_version ON itinerary_excluded_place (itinerary_version_id);

COMMENT ON COLUMN itinerary_excluded_place.itinerary_version_id IS
    '판마다 복사되는 스냅샷이다. itinerary_item·itinerary_leg 와 같은 이유 — 판을 새로 만들 때마다 바탕 판의 제외 목록을 그대로 물려받아야, 재계산을 몇 번 해도 한 번 뺀 장소가 다시 나오지 않는다.';
COMMENT ON COLUMN itinerary_excluded_place.operational_reason IS
    '사용자 자유 입력이다. 이벤트 payload 에는 싣지 않는다 — 문장에 개인정보가 실릴 수 있다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- itinerary_versions — 판 수준 경고 코드
-- ══════════════════════════════════════════════════════════════════════════════
ALTER TABLE itinerary_versions ADD COLUMN warning_codes VARCHAR(64)[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN itinerary_versions.warning_codes IS
    '항목이 아예 없는 시간대에 대한 경고(예: RECALC_NO_CANDIDATE)를 담는다. itinerary_item 은 항목이 있어야만 존재하는 행이라 그 칸에는 적을 자리가 없어 판에 적는다.';

-- ══════════════════════════════════════════════════════════════════════════════
-- recommendation_job — 편집 Job 의 결과도 같은 CHECK 로 지킨다
-- ══════════════════════════════════════════════════════════════════════════════
ALTER TABLE recommendation_job DROP CONSTRAINT ck_recommendation_job_result_present;

ALTER TABLE recommendation_job ADD CONSTRAINT ck_recommendation_job_result_present
    CHECK (job_status <> 'SUCCEEDED'
           OR job_type NOT IN ('ITINERARY_GENERATION', 'ITEM_REMOVE', 'ITINERARY_RECALCULATE')
           OR (itinerary_id IS NOT NULL AND itinerary_version IS NOT NULL)) NOT VALID;
