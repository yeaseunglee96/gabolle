// 장소의 일본어·중국어 이름 씨앗을 위키데이터(CC0)에서 만든다 — V20261002150000__place_local_names_wikidata.sql 의 INSERT 줄.
// S15P21E201-1948.  node backend/scripts/wikidata-local-names.mjs > seed.sql
//
// 부산 좌표 상자 안의 위키데이터 항목 중 한국어 이름이 있는 것을 받아, 일본어·간체·번체 이름이 한글 없이 적힌 것만 쓴다.
// 간체 칸은 zh-hans 가 없으면 zh 이름으로 채운다(위키데이터의 zh 는 대개 간체다). 이름 열쇠 규칙은 관광공사 이관
// (V20260930130000 · 이관 안 SQL 의 regexp_replace)과 같다 — 둘이 어긋나면 잇지 못한다.
const SPARQL = `SELECT ?item ?ko ?ja ?zhHans ?zhHant ?zh ?coord WHERE {
  SERVICE wikibase:box { ?item wdt:P625 ?coord .
    bd:serviceParam wikibase:cornerSouthWest "Point(128.75 34.88)"^^geo:wktLiteral .
    bd:serviceParam wikibase:cornerNorthEast "Point(129.32 35.40)"^^geo:wktLiteral . }
  ?item rdfs:label ?ko FILTER(LANG(?ko)="ko")
  OPTIONAL{?item rdfs:label ?ja FILTER(LANG(?ja)="ja")}
  OPTIONAL{?item rdfs:label ?zhHans FILTER(LANG(?zhHans)="zh-hans")}
  OPTIONAL{?item rdfs:label ?zhHant FILTER(LANG(?zhHant)="zh-hant")}
  OPTIONAL{?item rdfs:label ?zh FILTER(LANG(?zh)="zh")}
}`;

const response = await fetch(`https://query.wikidata.org/sparql?query=${encodeURIComponent(SPARQL)}`, {
  headers: { Accept: 'application/sparql-results+json', 'User-Agent': 'gabolle-busan-travel-app/1.0 (S15P21E201)' },
});
if (!response.ok) throw new Error(`위키데이터 ${response.status}`);
const bindings = (await response.json()).results.bindings;

const items = new Map();
for (const row of bindings) {
  const qid = row.item.value.split('/').pop();
  const item = items.get(qid) ?? { qid, ko: row.ko.value };
  for (const field of ['ja', 'zhHans', 'zhHant', 'zh']) if (row[field]) item[field] = row[field].value;
  const [, lng, lat] = row.coord.value.match(/Point\(([-0-9.]+) ([-0-9.]+)\)/);
  Object.assign(item, { lat: Number(lat), lng: Number(lng) });
  items.set(qid, item);
}

const hasHangul = (text) => /[가-힣ㄱ-ㅎ]/.test(text ?? '');
const key = (text) => text.toLowerCase().replace(/[(（][^)）]*[)）]/g, '').replace(/[^0-9a-z가-힣]/g, '');
const sql = (text) => `'${text.replace(/'/g, "''")}'`;
const rows = [];
for (const item of items.values()) {
  const nameKey = key(item.ko);
  if (nameKey.length < 2) continue;
  const shortKey = nameKey.replace(/부산/g, '');
  const add = (lang, value) => {
    if (!value || hasHangul(value) || value.length > 200) return;
    rows.push(`    (${sql(lang)}, ${sql(value.trim())}, ${sql(nameKey)}, ${sql(shortKey)}, ${item.lat}, ${item.lng}, ${sql(item.qid)}),  -- ${item.ko}`);
  };
  add('ja', item.ja);
  add('zh-Hans', item.zhHans ?? item.zh);
  add('zh-Hant', item.zhHant);
}
// 마지막 줄은 쉼표 없이
console.log(rows.join('\n').replace(/,  -- ([^\n]*)$/, '   -- $1'));
console.error(`항목 ${items.size} · 줄 ${rows.length}`);
