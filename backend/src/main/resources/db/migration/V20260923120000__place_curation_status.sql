-- S15P21E201-1426 — 기록(story)에 카카오에서 고른 장소를 붙일 수 있게 한다.
--
-- 왜. story.place_id 가 기록과 장소 자료를 잇는 유일한 다리인데, 2026-09-23 운영 실측에서
-- 원글 28건 중 5건에만 채워져 있었다. 글 작성 화면이 우리 DB 장소와 카카오 검색 결과를
-- 섞어 보여주는데 카카오 결과에는 place_id 가 없어서 안 실린다(frontend compose.tsx:99 의
-- 주석이 그대로 그렇게 적고 있다). 비어 있으면 글 순위 개인화·STORY_LIKE 를 취향 신호로
-- 쓰기·장소 기준 기록 묶어보기가 전부 성립하지 않는다.
--
-- 이 파일이 만드는 것은 둘이다. 칸을 더하는 것뿐이고 기존 행의 값은 안 바꾼다.

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. (source_type, source_id) 는 장소 하나를 가리킨다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- place 에 source_type·source_id 는 2026-09-04(V20260904020000)부터 있었지만 유일성이
-- 없었다. 그래서 같은 카카오 장소를 두 사람이 고르면 행이 둘 생긴다 — 그러면 "이 장소의
-- 기록 모아보기" 가 반씩 갈린다.
--
-- 부분 색인인 이유. 적재기 대부분은 두 칸을 채우지만 안 채운 행도 있고, PostgreSQL 은
-- NULL 을 서로 다르게 보므로 그냥 UNIQUE 를 걸면 빈 행이 전부 제약을 빠져나간다.
-- place_feature 의 uq_place_feature_keyed 가 같은 함정을 같은 방법으로 막고 있다.
--
-- 지금 걸 수 있는 근거: 2026-09-23 운영 실측에서 (source_type, source_id) 중복이 0건이다.
-- 적재기들은 원천 식별자에서 place_id 를 결정적으로 만들어(placeIdOf) 재적재가 PK 로
-- 덮어쓰이므로 중복을 만들지 않는다.
CREATE UNIQUE INDEX uq_place_source
    ON place (source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. "어디서 왔나" 와 "추천에 써도 되나" 는 다른 질문이다
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 티켓은 "추천 후보에는 당분간 사용자 출처를 넣지 않는다 - 품질이 검증되지 않았다" 고
-- 적었고 그 판단은 옳다. 그런데 그것을 source_type 으로 가르면 안 된다.
--
-- source_type = 'KAKAO_LOCAL' 인 행이 이미 넷 있다 - 해운대해수욕장·광안리해수욕장·
-- 감천문화마을·범어사다(V20260916210000, S15P21E201-76). 앱의 대표 카드가 쓰는
-- 핵심 명소이고 추천에 반드시 들어가야 한다. 출처로 걸러내면 이 넷이 같이 사라진다.
--
-- 그래서 칸을 나눈다. source_type 은 "어느 수집분에서 왔나"(출처), curation_status 는
-- "사람이 검증했나"(신뢰)다. place_feature 가 source_type 과 evidence_status 를 이미
-- 그렇게 나눠 둔 것과 같은 결이다.
--
-- 기본값이 CURATED 인 이유. 지금 있는 6,866행은 전부 적재기나 큐레이션이 넣은 것이라
-- 추천 후보로 쓰이고 있다. 기본을 USER_SUBMITTED 로 두면 이 이관이 도는 순간 추천이
-- 통째로 빈손이 된다.
ALTER TABLE place
    ADD COLUMN curation_status VARCHAR(20) NOT NULL DEFAULT 'CURATED';

ALTER TABLE place
    ADD CONSTRAINT ck_place_curation_status
        CHECK (curation_status IN ('CURATED', 'USER_SUBMITTED'));

COMMENT ON COLUMN place.curation_status IS
    'CURATED = 적재기·큐레이션이 넣었고 추천 후보로 쓴다. USER_SUBMITTED = 사용자가 기록에 붙이려고 고른 장소라 서버가 만들었고, 품질이 검증되지 않아 추천 후보에서 뺀다 (S15P21E201-1426). 출처(source_type)와 다른 질문이다 - KAKAO_LOCAL 이면서 CURATED 인 행이 넷 있다.';

-- 추천 후보 질의가 이 칸으로 거른다. 6,866행 중 거의 전부가 CURATED 라 선택도가 낮지만,
-- 거르는 쪽(USER_SUBMITTED)이 소수라 부분 색인이면 작고 효과가 있다.
CREATE INDEX ix_place_user_submitted
    ON place (place_id)
    WHERE curation_status = 'USER_SUBMITTED';
