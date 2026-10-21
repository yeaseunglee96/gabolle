import csv,json
q=lambda s:"'"+s.replace("'","''")+"'"
names=list(csv.DictReader(open('changes-names.csv',encoding='utf-8-sig')))
hours=list(csv.DictReader(open('changes-hours.csv',encoding='utf-8-sig')))
o=[]
o.append("""-- S15P21E201-2030 — 장소 외국어 이름(영어·일본어·중국어 간체·번체)과 영업시간의 빈칸을 공개 자료로 채운다.
--
-- 왜: 추천에 나오는 부산 장소 5,227곳(CURATED, 숙소 제외) 가운데 영어 이름이 있는 곳이 308곳(6%)뿐이라
--     외국어 화면에서 장소 이름이 거의 다 한국어였다. 영업시간도 1,071곳(20%)뿐이었다.
--
-- 출처(우선순위 순 — 같은 칸에 둘이 맞으면 앞의 것):
--   1) 한국관광공사 다국어 관광정보(EngService2·JpnService2·ChsService2·ChtService2, 2026-10-09 받음, 공공누리).
--      다국어 contentid 는 국문과 다른 번호라 제목 끝 괄호 속 한국어 이름 + 좌표 500m 로 잇는다(V20260930130000 과 같은 규칙).
--   2) OpenStreetMap(ODbL — 출처 표시) — 우리 장소가 OSM 에서 온 경우 «같은 OSM 번호» 항목의 name:en·name:ja·name:zh 태그.
--      영어 이름은 한국어 이름을 로마자로 바꾼 앞 세 글자가 들어 있을 때만 쓴다(뜻풀이·다른 이름 거르기 — 163곳 버림).
--      체인점은 brand:en·brand:wikidata 가 있으면 통과.
--   3) 위키데이터(CC0) — 한국어 이름 열쇠가 같고 500m 안.
--   영업시간: 같은 OSM 항목의 opening_hours 중 요일·시간이 단순한 꼴만(모든 요일이 정해지는 것). 자정을 넘기면 24:00 에서 자른다.
-- 기계 번역·지어낸 이름은 없다. 베트남어 이름은 칸이 없다 — 앱이 영어로 물러선다.
-- 이미 값이 있는 칸은 덮지 않는다. place_id 로 잇는다(장소 번호는 출처에서 정해지는 고정 값). 빈 시험 DB 에서는 0행.
-- 만든 스크립트·바뀐 목록: backend/scripts/place-enrichment-20261009/ (build_changes.py, changes-*.csv)
CREATE TEMPORARY TABLE place_name_fill (place_id UUID NOT NULL, col VARCHAR(20) NOT NULL, val VARCHAR(200) NOT NULL);
INSERT INTO place_name_fill (place_id, col, val) VALUES""")
o.append(',\n'.join(f"    ({q(r['place_id'])}, {q(r['column'])}, {q(r['new'])})  -- {r['name_ko'].replace(chr(10),' ')} · {r['source']}" .replace('--','--',1) for r in names))
# 마지막 쉼표가 주석 앞에 오도록 다시 짠다
o.pop()
lines=[f"    ({q(r['place_id'])}, {q(r['column'])}, {q(r['new'])})" for r in names]
cm=[f"  -- {r['name_ko']} · {r['source']}" for r in names]
o.append('\n'.join(l+(',' if i<len(lines)-1 else ';')+cm[i] for i,l in enumerate(lines)))
for col in ['name_en','name_ja','name_zh_hans','name_zh_hant']:
    o.append(f"UPDATE place p SET {col} = f.val FROM place_name_fill f WHERE f.place_id = p.place_id AND f.col = '{col}' AND p.{col} IS NULL;")
o.append("DROP TABLE place_name_fill;\n")
o.append("CREATE TEMPORARY TABLE place_hours_fill (place_id UUID NOT NULL, osm_ref VARCHAR(40) NOT NULL, val JSONB NOT NULL);\nINSERT INTO place_hours_fill (place_id, osm_ref, val) VALUES")
hl=[f"    ({q(r['place_id'])}, {q(r['osm'])}, {q(r['value'])})" for r in hours]
o.append('\n'.join(l+(',' if i<len(hl)-1 else ';')+f"  -- {hours[i]['name_ko']}" for i,l in enumerate(hl)))
o.append("""INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status, source_type, source_id, observed_at, source_version, created_at)
SELECT gen_random_uuid(), h.place_id, 'OPENING_HOURS', NULL, h.val, 'ESTIMATED', 'OSM', h.osm_ref, TIMESTAMPTZ '2026-10-09 00:00:00+09', 'osm-opening-hours-20261009', now()
FROM place_hours_fill h JOIN place p ON p.place_id = h.place_id
WHERE NOT EXISTS (SELECT 1 FROM place_feature f WHERE f.place_id = h.place_id AND f.feature_type = 'OPENING_HOURS');
DROP TABLE place_hours_fill;
""")
open('../../src/main/resources/db/migration/V20261009210000__place_names_hours_enrichment.sql','w',encoding='utf-8',newline='\n').write('\n'.join(o))
print(len(names),len(hours))
