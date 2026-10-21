// S15P21E201-2059 — 현지인 설문 추천 47곳을 운영 DB 에 넣는 멱등 SQL 을 만든다.
//
//   node make_sql.mjs                       → local_picks_20261015.sql 을 이 폴더에 쓴다
//   node make_sql.mjs <입력.ndjson> <출력.sql>
//
// 입력(survey-local-picks-20261015.ndjson)은 한 줄에 JSON 하나다.
//   {"placeId":"110ae30a-…","name":"로빈뮤지엄","category":"CAFE_HEALING","gu":"강서구","dong":"신호동",
//    "recommenders":1,"businessType":"커피숍","existingTags":""}
// category 는 FOOD(맛집) 또는 CAFE_HEALING(카페)이다. recommenders 가 null 이면 추천 인원을 모르는 곳이다.
// businessType · existingTags 는 사람이 보라고 남긴 칸이라 SQL 에 쓰지 않는다 — 이미 달린 표식은 SQL 이 DB 에서 직접 본다.
//
// 🔴 SQL 은 이 스크립트가 만든다. SQL 파일을 손으로 고치면 다음에 이 스크립트를 돌릴 때 그 수정이 사라진다 — 고치려면 이 파일을 고친다.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const input = process.argv[2] ?? join(HERE, 'survey-local-picks-20261015.ndjson');
const output = process.argv[3] ?? join(HERE, 'local_picks_20261015.sql');
const VERSION = 'survey-local-picks-20261015';

const rows = readFileSync(input, 'utf8')
  .split(/\r?\n/)
  .filter((line) => line.trim())
  .map((line, index) => {
    const row = JSON.parse(line);
    const where = `${index + 1}번째 줄`;
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(row.placeId)) throw new Error(`${where}: placeId 가 UUID 가 아니다 — ${row.placeId}`);
    if (!['FOOD', 'CAFE_HEALING'].includes(row.category)) throw new Error(`${where}: category 는 FOOD 또는 CAFE_HEALING 이어야 한다 — ${row.category}`);
    for (const field of ['name', 'gu', 'dong']) if (typeof row[field] !== 'string' || !row[field].trim()) throw new Error(`${where}: ${field} 가 비었다`);
    if (/[/]/.test(row.gu + row.dong)) throw new Error(`${where}: 구·동 이름에 '/' 가 있다 — 열쇠가 구와 동을 '/' 로 나눈다`);
    if (row.recommenders !== null && !(Number.isInteger(row.recommenders) && row.recommenders >= 0)) throw new Error(`${where}: recommenders 는 0 이상 정수거나 null 이어야 한다 — ${row.recommenders}`);
    return row;
  });

const ids = new Set(rows.map((row) => row.placeId));
if (ids.size !== rows.length) throw new Error('placeId 가 겹치는 줄이 있다');

const q = (text) => `'${String(text).replaceAll("'", "''")}'`;
const values = rows
  .map((row) => `    (${q(row.placeId)}, ${q(row.name)}, ${q(row.category)}, ${q(row.gu)}, ${q(row.dong)}, ${row.recommenders === null ? 'NULL' : row.recommenders})`)
  .join(',\n');
const food = rows.filter((row) => row.category === 'FOOD').length;
const cafe = rows.length - food;

const sql = `-- S15P21E201-2059 — 현지인 설문 추천 ${rows.length}곳(맛집 ${food} · 카페 ${cafe})을 운영 DB 에 단다.  수집분: ${VERSION}
--
-- 🔴 이 파일은 backend/scripts/local-picks-20261015/make_sql.mjs 가 만든다. 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 🔴 배포(마이그레이션 V20261015120000__local_survey_and_cafe)가 먼저다. 아래 첫 확인이 사전에 낱말이 없으면 여기서 멈춘다.
-- 🔴 두 번 돌려도 같다(멱등) — 이미 있는 표식은 건드리지 않고, 한 번 옮긴 곳은 다시 안 옮긴다. 한 트랜잭션이라 중간에 죽으면 아무것도 안 바뀐다.
--
-- 돌리는 법 (서버의 postgres 컨테이너 — 이름·접속값은 backend/docs/PLACE-DATA-LOAD.md 8-1 과 같다)
--   scp -i $PEM local_picks_20261015.sql $HOST:/home/ubuntu/load/
--   ssh -i $PEM $HOST "docker exec -i local-route-personalization-postgres-1 psql -U app_user -d app_db -v ON_ERROR_STOP=1 < /home/ubuntu/load/local_picks_20261015.sql"
-- 출력의 「확인」 표들을 읽는다 — 특히 「확인 1」(DB 에 없거나 추천에 못 쓰는 곳)이 비어 있어야 ${rows.length}곳이 다 달린다.
--
-- 하는 일
--   0) 사전에 새 낱말이 있는지 본다 (없으면 멈춘다)
--   1) 이미 맛집(LOCAL_RESTAURANT)이 달린 공공 인증 장소 가운데 장소 갈래(category)가 CAFE_HEALING 인 곳을 카페(LOCAL_CAFE)로 옮긴다
--      · 몇 곳인지 먼저 센다. 카페 표식이 이미 있는 곳은 맛집 표식만 지운다(같은 표식이 둘이면 uq_place_feature_keyed 에 걸린다)
--      · 옮기기는 열쇠만 바꾼다 — 표식의 출처·수집분·증거 등급은 그대로라 어느 적재가 단 표식인지 되짚을 수 있다
--   2) 설문 추천 ${rows.length}곳에 표식을 단다 (이미 있는 것은 건너뛴다)
--      · 갈래: FOOD → INTEREST_TAG/LOCAL_RESTAURANT · CAFE_HEALING → INTEREST_TAG/LOCAL_CAFE
--      · 구·동: ADMIN_AREA_TAG (한 장소에 하나 — 이미 있으면 안 단다)
--      · 현지인 추천: CERTIFICATION_TAG/LOCAL_SURVEY  값 {"source":"현지인 추천","recommenders":n}  (n 을 모르면 칸이 없다)
--   3) 결과를 센다
--
-- 표식 id 는 이름 기반 UUID(MD5 로 만든 같은 입력 → 같은 값)다. 맛집 적재기(CertifiedRestaurantLoader.featureIdOf)와 같은 식이고
-- 자바의 UUID.nameUUIDFromBytes 와 같은 값이 나온다 — LocalPicksSqlIntegrationTest 가 두 값을 맞춰 본다.
--
-- 되돌리기 (backend/docs/PLACE-DATA-LOAD.md 13절에도 있다)
--   DELETE FROM gabolle.place_feature WHERE source_type = 'LOCAL_SURVEY' AND source_version = '${VERSION}';
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

-- ── 설문 추천 ${rows.length}곳 ──────────────────────────────────────────────────────────
CREATE TEMP TABLE pick (
    place_id     uuid PRIMARY KEY,
    name         text NOT NULL,
    category     text NOT NULL CHECK (category IN ('FOOD', 'CAFE_HEALING')),
    gu           text NOT NULL,
    dong         text NOT NULL,
    recommenders integer
) ON COMMIT DROP;

INSERT INTO pick (place_id, name, category, gu, dong, recommenders) VALUES
${values};

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
       'LOCAL_SURVEY', t.place_id::text, NULL, '${VERSION}', now()
  FROM target t
 WHERE NOT EXISTS (SELECT 1 FROM place_feature f
                    WHERE f.place_id = t.place_id AND f.feature_type = 'INTEREST_TAG' AND f.feature_key = t.explore_key)
ON CONFLICT DO NOTHING;

-- 2-2 구·동 — 한 장소에 하나. 이미 있으면 파일의 구·동이 달라도 먼저 있던 것을 둔다. 좌표를 역지오코딩해 얻은 값이라 추정(ESTIMATED)이다.
INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
                           source_type, source_id, observed_at, source_version, created_at)
SELECT pg_temp.name_uuid('gabolle:place_feature:LOCAL_SURVEY:' || t.place_id || ':ADMIN_AREA_TAG:' || t.gu || '/' || t.dong),
       t.place_id, 'ADMIN_AREA_TAG', t.gu || '/' || t.dong, jsonb_build_object('gu', t.gu, 'dong', t.dong), 'ESTIMATED',
       'LOCAL_SURVEY', t.place_id::text, NULL, '${VERSION}', now()
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
       'VERIFIED', 'LOCAL_SURVEY', t.place_id::text, NULL, '${VERSION}', now()
  FROM target t
 WHERE NOT EXISTS (SELECT 1 FROM place_feature f
                    WHERE f.place_id = t.place_id AND f.feature_type = 'CERTIFICATION_TAG' AND f.feature_key = 'LOCAL_SURVEY')
ON CONFLICT DO NOTHING;

-- ── 3. 결과 ───────────────────────────────────────────────────────────────
SELECT '확인 4: 달린 표식(이 수집분)' AS 단계, f.feature_type, f.feature_key, count(*) AS 줄수
  FROM place_feature f
 WHERE f.source_type = 'LOCAL_SURVEY' AND f.source_version = '${VERSION}'
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
`;

writeFileSync(output, sql, 'utf8');
console.log(`${rows.length}곳(맛집 ${food} · 카페 ${cafe}) → ${output}`);
