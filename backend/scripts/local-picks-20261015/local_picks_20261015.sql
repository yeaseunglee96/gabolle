-- S15P21E201-2059 — 현지인 설문 추천 47곳(맛집 35 · 카페 12)을 운영 DB 에 단다.  수집분: survey-local-picks-20261015
--
-- 🔴 이 파일은 backend/scripts/local-picks-20261015/make_sql.mjs 가 만든다. 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 🔴 배포(마이그레이션 V20261015120000__local_survey_and_cafe)가 먼저다. 아래 첫 확인이 사전에 낱말이 없으면 여기서 멈춘다.
-- 🔴 두 번 돌려도 같다(멱등) — 이미 있는 표식은 건드리지 않고, 한 번 옮긴 곳은 다시 안 옮긴다. 한 트랜잭션이라 중간에 죽으면 아무것도 안 바뀐다.
--
-- 돌리는 법 (서버의 postgres 컨테이너 — 이름·접속값은 backend/docs/PLACE-DATA-LOAD.md 8-1 과 같다)
--   scp -i $PEM local_picks_20261015.sql $HOST:/home/ubuntu/load/
--   ssh -i $PEM $HOST "docker exec -i local-route-personalization-postgres-1 psql -U app_user -d app_db -v ON_ERROR_STOP=1 < /home/ubuntu/load/local_picks_20261015.sql"
-- 출력의 「확인」 표들을 읽는다 — 특히 「확인 1」(DB 에 없거나 추천에 못 쓰는 곳)이 비어 있어야 47곳이 다 달린다.
--
-- 하는 일
--   0) 사전에 새 낱말이 있는지 본다 (없으면 멈춘다)
--   1) 이미 맛집(LOCAL_RESTAURANT)이 달린 공공 인증 장소 가운데 장소 갈래(category)가 CAFE_HEALING 인 곳을 카페(LOCAL_CAFE)로 옮긴다
--      · 몇 곳인지 먼저 센다. 카페 표식이 이미 있는 곳은 맛집 표식만 지운다(같은 표식이 둘이면 uq_place_feature_keyed 에 걸린다)
--      · 옮기기는 열쇠만 바꾼다 — 표식의 출처·수집분·증거 등급은 그대로라 어느 적재가 단 표식인지 되짚을 수 있다
--   2) 설문 추천 47곳에 표식을 단다 (이미 있는 것은 건너뛴다)
--      · 갈래: FOOD → INTEREST_TAG/LOCAL_RESTAURANT · CAFE_HEALING → INTEREST_TAG/LOCAL_CAFE
--      · 구·동: ADMIN_AREA_TAG (한 장소에 하나 — 이미 있으면 안 단다)
--      · 현지인 추천: CERTIFICATION_TAG/LOCAL_SURVEY  값 {"source":"현지인 추천","recommenders":n}  (n 을 모르면 칸이 없다)
--   3) 결과를 센다
--
-- 표식 id 는 이름 기반 UUID(MD5 로 만든 같은 입력 → 같은 값)다. 맛집 적재기(CertifiedRestaurantLoader.featureIdOf)와 같은 식이고
-- 자바의 UUID.nameUUIDFromBytes 와 같은 값이 나온다 — LocalPicksSqlIntegrationTest 가 두 값을 맞춰 본다.
--
-- 되돌리기 (backend/docs/PLACE-DATA-LOAD.md 13절에도 있다)
--   DELETE FROM gabolle.place_feature WHERE source_type = 'LOCAL_SURVEY' AND source_version = 'survey-local-picks-20261015';
--   -- 카페로 옮긴 곳을 맛집으로 되돌리려면 (옮긴 표식은 출처가 BUSAN_CERTIFIED_FOOD 로 남아 있다)
--   UPDATE gabolle.place_feature f SET feature_key = 'LOCAL_RESTAURANT' FROM gabolle.place p
--    WHERE p.place_id = f.place_id AND p.category = 'CAFE_HEALING' AND f.feature_type = 'INTEREST_TAG'
--      AND f.feature_key = 'LOCAL_CAFE' AND f.source_type = 'BUSAN_CERTIFIED_FOOD';
BEGIN;

-- 운영 표는 gabolle 스키마에 있다. 시험 DB 처럼 스키마가 없으면 public 을 쓴다.
SET LOCAL search_path TO gabolle, public;

-- ── 0. 사전에 새 낱말이 있어야 한다 ───────────────────────────────────────────
DO $$
BEGIN
    IF (SELECT count(*) FROM place_feature_code
         WHERE (feature_type, feature_key) IN (('INTEREST_TAG', 'LOCAL_CAFE'), ('CERTIFICATION_TAG', 'LOCAL_SURVEY'))) < 2 THEN
        RAISE EXCEPTION '사전(place_feature_code)에 LOCAL_CAFE · LOCAL_SURVEY 가 없다 — 배포(V20261015120000)가 먼저 나가야 한다';
    END IF;
END
$$;

-- 이름 기반 UUID — 자바 UUID.nameUUIDFromBytes(글자.getBytes(UTF_8)) 와 같은 값이다(MD5 → 버전 3 · 변종 비트).
CREATE OR REPLACE FUNCTION pg_temp.name_uuid(seed text) RETURNS uuid
LANGUAGE sql IMMUTABLE AS $$
    SELECT (substr(h, 1, 12) || '3' || substr(h, 14, 3)
            || to_hex((('x' || substr(h, 17, 1))::bit(4)::int & 3) | 8)
            || substr(h, 18, 15))::uuid
      FROM (SELECT md5(seed) AS h) t
$$;

-- ── 설문 추천 47곳 ──────────────────────────────────────────────────────────
CREATE TEMP TABLE pick (
    place_id     uuid PRIMARY KEY,
    name         text NOT NULL,
    category     text NOT NULL CHECK (category IN ('FOOD', 'CAFE_HEALING')),
    gu           text NOT NULL,
    dong         text NOT NULL,
    recommenders integer
) ON COMMIT DROP;

INSERT INTO pick (place_id, name, category, gu, dong, recommenders) VALUES
    ('110ae30a-4bba-3120-aa06-f62dcc889cc7', '로빈뮤지엄', 'CAFE_HEALING', '강서구', '신호동', 1),
    ('549df43c-8744-3969-9d22-b06416b50288', '풍년곱창', 'FOOD', '남구', '감만동', 1),
    ('9b89704a-5ee0-353d-8110-da7ae55f5b21', '영진국밥(코스트코센텀점)', 'FOOD', '수영구', '망미동', 1),
    ('06172389-d824-3380-94a0-3becd33221c7', '대궐안집', 'FOOD', '사상구', '괘법동', 2),
    ('128cdf04-ff8a-30ce-8c32-629f5f397217', '모모타로', 'FOOD', '연제구', '연산동', 1),
    ('369ea463-8f18-376e-8d22-ce4d76fcc773', '버드나무집', 'FOOD', '부산진구', '전포동', 1),
    ('5e6ddfcd-0e7e-365e-b0f9-dfc5767f6206', '복동이네 오지상 라멘', 'FOOD', '부산진구', '전포동', 1),
    ('4cb60fd4-518c-3211-8d1f-f6e4d5b58efe', '프루토 프루타', 'CAFE_HEALING', '부산진구', '전포동', 1),
    ('49521157-009e-31d3-9647-b7a6b5988c52', '수월경화', 'FOOD', '해운대구', '송정동', 1),
    ('3a55a8b0-3a56-3948-a668-0d2954399409', '크레페아인스', 'FOOD', '중구', '광복동2가', 1),
    ('3352e40f-5668-306c-9d44-2ab4d0d3f4a8', '희와제과', 'CAFE_HEALING', '부산진구', '전포동', 1),
    ('3fb83eab-1388-3d8d-9e45-e3c83cb167ea', '경찰서옆초밥집', 'FOOD', '부산진구', '부전동', 1),
    ('0ee1dde8-d692-33cf-9619-df51b6a687c4', '스콜', 'FOOD', '부산진구', '부전동', 1),
    ('c69a6ee6-f295-3bbe-af17-509f832b9b5d', '스톤스트리트', 'FOOD', '중구', '대청동2가', 1),
    ('4b9ae3a8-b356-30ed-977a-f8fdeddab996', '안목', 'FOOD', '수영구', '남천동', 1),
    ('80583d4b-8af0-3fca-baf1-5356a57aee28', '수중어초 횟집', 'FOOD', '금정구', '구서동', 1),
    ('e560277d-74fa-3d86-8be3-8fbb966dfeb7', '에타리', 'CAFE_HEALING', '금정구', '장전동', 3),
    ('145c3b20-ce6b-3b91-891e-90d3506bbb8c', '깡돼후야시장', 'FOOD', '중구', '부평동2가', 2),
    ('31ce10d7-ea21-3e42-81c0-7c80f2f1c177', '치킨버거클럽', 'FOOD', '부산진구', '전포동', 2),
    ('314c5624-7b19-3c4f-8243-3fc034f5fbe7', '히타로', 'CAFE_HEALING', '동래구', '사직동', 2),
    ('20cea826-98dc-35a6-af34-ed910f1f934c', '썰스데이파티', 'FOOD', '부산진구', '부전동', 1),
    ('f1bdff3a-70dc-39c6-b478-f0d325adbb23', '툼브로이', 'FOOD', '해운대구', '송정동', 1),
    ('f99b3723-9df2-368b-b967-552e10893b0d', '1등가마솥돼지국밥(남포점)', 'FOOD', '중구', '부평동2가', 1),
    ('6d956714-953b-3597-bbea-74ca74f19ccd', '냉수탕가든', 'FOOD', '부산진구', '가야동', 1),
    ('cb5611f6-1f2a-3fec-984f-0c7f3c988e47', '노적봉', 'FOOD', '사하구', '하단동', 1),
    ('ecaa9900-d389-3d6c-8677-ff6264b10779', '마산곱창', 'FOOD', '부산진구', '부암동', 1),
    ('3064e7e3-fbc2-39c1-a89d-1e93a6ad6e73', '모루식당', 'FOOD', '부산진구', '전포동', 1),
    ('a9e42acf-08b2-34a1-bb2d-aca3979d952b', '백세촌 순대돼지국밥', 'FOOD', '영도구', '청학동', 1),
    ('0ff784d5-d21c-32a7-a6d8-7a8d63b55bed', '봉평메밀막국수', 'FOOD', '동래구', '안락동', 1),
    ('9b4d0895-c87e-335d-a774-0f70fcde0dce', '새옹지마', 'FOOD', '중구', '광복동1가', 1),
    ('3b20d2e9-c85d-37c4-9367-1796ed372459', '성원칼국수', 'FOOD', '부산진구', '개금동', 1),
    ('d5429609-a969-3024-840e-8fc702fdc9d3', '송미분식', 'FOOD', '부산진구', '전포동', 1),
    ('17290acf-6da9-305c-8b55-ea54d2b9b5c8', '오리궁디', 'FOOD', '사상구', '괘법동', 1),
    ('1b6ebd76-6cf0-3a70-8f67-092c35be00f1', '유림부대밀면', 'FOOD', '금정구', '장전동', 1),
    ('6ea5dd6b-e8e0-32ef-947c-bc2fb57de7b8', '이탈리아피자', 'FOOD', '부산진구', '부암동', 1),
    ('9e3e3678-3098-35e5-8709-3cb4c66c45e7', '창현수산', 'FOOD', '동구', '범일동', 1),
    ('6c838168-aa56-3dfe-b78c-60e5f3fb4db6', '고메', 'CAFE_HEALING', '부산진구', '전포동', 1),
    ('f9fbf41c-b5a2-3aa5-8bb2-2018ff33ccf9', '더프커피', 'CAFE_HEALING', '사하구', '감천동', 1),
    ('4e1f3868-ba43-3495-8231-3599e93b1c11', '레이지모먼트커피스탠드', 'CAFE_HEALING', '동래구', '수안동', 1),
    ('1984a85b-8e10-34af-ae51-2ab95e58fe40', '사랑방다실', 'CAFE_HEALING', '부산진구', '전포동', 1),
    ('f00087bb-fc16-3a00-b797-13188f14f383', '아브라함커피', 'CAFE_HEALING', '영도구', '동삼동', 1),
    ('f8581108-72a5-3f4f-90a5-15f9b27ac674', '진수밥상', 'FOOD', '부산진구', '당감동', 1),
    ('12719efa-0897-319b-8e01-959d74d327d9', '차마당', 'CAFE_HEALING', '부산진구', '부전동', 1),
    ('9f117af5-c844-34d7-8db4-4977a9c5589c', '카페 824', 'CAFE_HEALING', '기장군', '기장읍', 1),
    ('591bd533-2f0c-3b21-a1ab-62cb7cb2a9c4', '밀양가산돼지국밥', 'FOOD', '동구', '범일동', NULL),
    ('0486e08d-469b-3dd6-ae46-e1aae9c6704d', '수영본가돼지국밥', 'FOOD', '부산진구', '부전동', 1),
    ('ccabd1ab-1161-32f1-b0b5-6d951f5b752d', '고복샤브샤브(서면점)', 'FOOD', '부산진구', '부전동', 1);

-- ── 확인 1. 달 수 없는 곳 — DB 에 없거나, 추천에서 빠졌거나(숨김·합쳐짐), 폐업일자가 있다 ──────────
SELECT '확인 1: 달 수 없는 곳(비어 있어야 한다)' AS 단계, k.name, k.place_id,
       CASE WHEN p.place_id IS NULL THEN '장소가 DB 에 없다'
            WHEN p.curation_status <> 'CURATED' THEN '추천에서 빠진 곳 ' || p.curation_status
            ELSE '폐업일자 ' || p.closed_on END AS 이유
  FROM pick k LEFT JOIN place p ON p.place_id = k.place_id
 WHERE p.place_id IS NULL OR p.curation_status <> 'CURATED' OR p.closed_on IS NOT NULL;

-- ── 확인 2. 파일의 갈래와 DB 의 장소 갈래가 다른 곳 — 알려 주기만 한다(표식은 파일의 갈래를 따른다) ──
SELECT '확인 2: 파일의 갈래와 DB 의 갈래가 다른 곳' AS 단계, k.name, k.category AS 파일, p.category AS db
  FROM pick k JOIN place p ON p.place_id = k.place_id
 WHERE p.category IS DISTINCT FROM k.category;

-- ── 1. 맛집에 든 카페를 카페로 옮긴다 ───────────────────────────────────────────
SELECT '확인 3: 맛집에서 카페로 옮길 곳(장소 갈래 CAFE_HEALING + 맛집 표식)' AS 단계, count(*) AS 곳수,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM place_feature c
                                       WHERE c.place_id = f.place_id AND c.feature_type = 'INTEREST_TAG'
                                         AND c.feature_key = 'LOCAL_CAFE')) AS 그중_카페_표식도_있는_곳
  FROM place_feature f JOIN place p ON p.place_id = f.place_id
 WHERE f.feature_type = 'INTEREST_TAG' AND f.feature_key = 'LOCAL_RESTAURANT' AND p.category = 'CAFE_HEALING';

DELETE FROM place_feature f
 USING place p
 WHERE p.place_id = f.place_id AND p.category = 'CAFE_HEALING'
   AND f.feature_type = 'INTEREST_TAG' AND f.feature_key = 'LOCAL_RESTAURANT'
   AND EXISTS (SELECT 1 FROM place_feature c
                WHERE c.place_id = f.place_id AND c.feature_type = 'INTEREST_TAG' AND c.feature_key = 'LOCAL_CAFE');

UPDATE place_feature f
   SET feature_key = 'LOCAL_CAFE'
  FROM place p
 WHERE p.place_id = f.place_id AND p.category = 'CAFE_HEALING'
   AND f.feature_type = 'INTEREST_TAG' AND f.feature_key = 'LOCAL_RESTAURANT';

-- ── 2. 설문 추천에 표식을 단다 ───────────────────────────────────────────────
-- 달 수 있는 곳만: 추천에 쓰는 장소(CURATED)이고 폐업일자가 없다. 아니면 위 「확인 1」에 나온다.
CREATE TEMP TABLE target ON COMMIT DROP AS
SELECT k.place_id, k.name, k.gu, k.dong, k.recommenders,
       CASE k.category WHEN 'FOOD' THEN 'LOCAL_RESTAURANT' ELSE 'LOCAL_CAFE' END AS explore_key
  FROM pick k JOIN place p ON p.place_id = k.place_id
 WHERE p.curation_status = 'CURATED' AND p.closed_on IS NULL;

-- 2-1 갈래 표식 — 추정(ESTIMATED): 설문 추천과 우리 장소 갈래에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
                           source_type, source_id, observed_at, source_version, created_at)
SELECT pg_temp.name_uuid('gabolle:place_feature:LOCAL_SURVEY:' || t.place_id || ':INTEREST_TAG:' || t.explore_key),
       t.place_id, 'INTEREST_TAG', t.explore_key, 'true'::jsonb, 'ESTIMATED',
       'LOCAL_SURVEY', t.place_id::text, NULL, 'survey-local-picks-20261015', now()
  FROM target t
 WHERE NOT EXISTS (SELECT 1 FROM place_feature f
                    WHERE f.place_id = t.place_id AND f.feature_type = 'INTEREST_TAG' AND f.feature_key = t.explore_key)
ON CONFLICT DO NOTHING;

-- 2-2 구·동 — 한 장소에 하나. 이미 있으면 파일의 구·동이 달라도 먼저 있던 것을 둔다. 좌표를 역지오코딩해 얻은 값이라 추정(ESTIMATED)이다.
INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
                           source_type, source_id, observed_at, source_version, created_at)
SELECT pg_temp.name_uuid('gabolle:place_feature:LOCAL_SURVEY:' || t.place_id || ':ADMIN_AREA_TAG:' || t.gu || '/' || t.dong),
       t.place_id, 'ADMIN_AREA_TAG', t.gu || '/' || t.dong, jsonb_build_object('gu', t.gu, 'dong', t.dong), 'ESTIMATED',
       'LOCAL_SURVEY', t.place_id::text, NULL, 'survey-local-picks-20261015', now()
  FROM target t
 WHERE NOT EXISTS (SELECT 1 FROM place_feature f WHERE f.place_id = t.place_id AND f.feature_type = 'ADMIN_AREA_TAG')
ON CONFLICT DO NOTHING;

-- 2-3 현지인 추천 — 설문에서 실제로 추천받았다는 기록이라 확인됨(VERIFIED). 추천한 사람 수를 모르면 칸을 뺀다(0 으로 채우지 않는다).
INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
                           source_type, source_id, observed_at, source_version, created_at)
SELECT pg_temp.name_uuid('gabolle:place_feature:LOCAL_SURVEY:' || t.place_id || ':CERTIFICATION_TAG:LOCAL_SURVEY'),
       t.place_id, 'CERTIFICATION_TAG', 'LOCAL_SURVEY',
       CASE WHEN t.recommenders IS NULL THEN jsonb_build_object('source', '현지인 추천')
            ELSE jsonb_build_object('source', '현지인 추천', 'recommenders', t.recommenders) END,
       'VERIFIED', 'LOCAL_SURVEY', t.place_id::text, NULL, 'survey-local-picks-20261015', now()
  FROM target t
 WHERE NOT EXISTS (SELECT 1 FROM place_feature f
                    WHERE f.place_id = t.place_id AND f.feature_type = 'CERTIFICATION_TAG' AND f.feature_key = 'LOCAL_SURVEY')
ON CONFLICT DO NOTHING;

-- ── 3. 결과 ───────────────────────────────────────────────────────────────
SELECT '확인 4: 달린 표식(이 수집분)' AS 단계, f.feature_type, f.feature_key, count(*) AS 줄수
  FROM place_feature f
 WHERE f.source_type = 'LOCAL_SURVEY' AND f.source_version = 'survey-local-picks-20261015'
 GROUP BY 2, 3 ORDER BY 2, 3;

SELECT '확인 5: 지금 갈래별 곳 수' AS 단계, f.feature_key, count(DISTINCT f.place_id) AS 곳수
  FROM place_feature f
 WHERE f.feature_type = 'INTEREST_TAG' AND f.feature_key IN ('LOCAL_RESTAURANT', 'LOCAL_CAFE')
 GROUP BY 2 ORDER BY 2;

SELECT '확인 6: 맛집·카페 양쪽에 선 곳(음식점을 겸하는 카페가 아니면 이상하다)' AS 단계, p.name_ko, p.category
  FROM place p
 WHERE EXISTS (SELECT 1 FROM place_feature a WHERE a.place_id = p.place_id AND a.feature_type = 'INTEREST_TAG' AND a.feature_key = 'LOCAL_RESTAURANT')
   AND EXISTS (SELECT 1 FROM place_feature b WHERE b.place_id = p.place_id AND b.feature_type = 'INTEREST_TAG' AND b.feature_key = 'LOCAL_CAFE');

DROP FUNCTION pg_temp.name_uuid(text);

COMMIT;
