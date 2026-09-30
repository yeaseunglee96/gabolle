// 부산 가까운 도움 자료(병원·의원·약국·경찰)를 만든다 — S15P21E201-1893.
//
//   node build-help-places.cjs <병원정보.json> <약국정보.json> <세부정보.json> <OSM poi.ndjson> <out.json>
//
// 입력
//   병원·약국·세부정보 — 건강보험심사평가원 「전국 병의원 및 약국 현황」(보건의료빅데이터개방시스템 opendata.hira.or.kr,
//     공공누리 제1유형 · 출처표시). zip 안의 1.병원정보서비스 · 2.약국정보서비스 · 4.의료기관별상세정보서비스_02_세부정보
//     xlsx 를 첫 시트 [머리, …행] JSON 으로 바꾼 것(README.md 의 xlsx2json.cjs)
//   OSM — bigData/data/raw/pbf/poi.ndjson(origin/main). 경찰(amenity=police)만 쓴다 — 심평원 자료에 경찰은 없다
//
// 🔴 심평원 좌표는 (X=경도, Y=위도) 순이다. 전화번호에 지역번호가 빠진 칸이 있어(「248-9000」) 부산이면 051 을 붙인다.
const fs = require('fs');
const [hospitalPath, pharmacyPath, detailPath, osmPath, out] = process.argv.slice(2);
if (!out) throw new Error('인자 다섯 개가 필요하다 — 맨 위 사용법');

const table = (file) => {
  const [head, ...rows] = JSON.parse(fs.readFileSync(file, 'utf8'));
  const index = Object.fromEntries(head.map((name, i) => [name, i]));
  return rows.map((row) => new Proxy({}, { get: (_, key) => (index[key] === undefined ? undefined : row[index[key]] ?? '') }));
};

// 여행자가 다치거나 아플 때 찾아갈 갈래만. 요양·정신병원, 치과, 한의원·한방병원, 조산원은 뺀다.
const HOSPITAL_TYPES = new Set(['상급종합', '종합병원', '병원', '의원', '보건소', '보건지소', '보건진료소']);
// 응급실 표시는 병원급 이상만 믿는다 — 세부정보의 응급실 칸이 의원에도 「Y」로 적힌 곳이 있었다(가족보건의원 등)
const ER_TYPES = new Set(['상급종합', '종합병원', '병원']);
// 이름으로 거른다 — 의원 중 피부·성형·미용은 급할 때 찾는 곳이 아니다
const EXCLUDE_NAME = /피부|성형|미용|요양|치과|한의|한방/;

const phoneOf = (raw) => {
  const digits = String(raw || '').replace(/[^\d-]/g, '');
  if (!digits) return null;
  return /^0/.test(digits) ? digits : `051-${digits}`;
};
const coord = (value) => {
  const n = Number(value);
  return Number.isFinite(n) && n !== 0 ? Math.round(n * 1e6) / 1e6 : null;
};

// 요일 진료시간 — 「900」「1830」 모양. 비었거나 0 이면 모른다(null). 일요일 칸이 비고 휴진 안내가 있으면 쉰다(closed)
const DAYS = [['mon', '월요일'], ['tue', '화요일'], ['wed', '수요일'], ['thu', '목요일'], ['fri', '금요일'], ['sat', '토요일'], ['sun', '일요일']];
const hhmm = (value) => {
  const n = Number(String(value || '').trim());
  if (!Number.isFinite(n) || n <= 0 || n > 2400 || n % 100 >= 60) return null;
  return n;
};
const hoursOf = (detail) => {
  if (!detail) return null;
  const hours = {};
  let any = false;
  for (const [key, ko] of DAYS) {
    const open = hhmm(detail[`진료시작시간_${ko}`]);
    const close = hhmm(detail[`진료종료시간_${ko}`]);
    if (open !== null && close !== null && close > open) { hours[key] = [open, close]; any = true; }
    else if (key === 'sun' && /휴진|휴무/.test(detail['휴진안내_일요일'] || '')) hours[key] = 'closed';
  }
  return any ? hours : null;
};

const details = new Map(table(detailPath).map((row) => [row['암호화요양기호'], row]));
const places = [];

for (const row of table(hospitalPath)) {
  if (row['시도코드명'] !== '부산' || !HOSPITAL_TYPES.has(row['종별코드명']) || EXCLUDE_NAME.test(row['요양기관명'])) continue;
  const lat = coord(row['좌표(Y)']);
  const lng = coord(row['좌표(X)']);
  if (lat === null || lng === null) continue;
  const detail = details.get(row['암호화요양기호']);
  const er = ER_TYPES.has(row['종별코드명']) && detail && (detail['응급실_주간_운영여부'] === 'Y' || detail['응급실_야간_운영여부'] === 'Y');
  places.push({ kind: 'HOSPITAL', type: row['종별코드명'], name: row['요양기관명'], address: row['주소'] || null, phone: phoneOf(row['전화번호']), lat, lng, emergency: Boolean(er), hours: hoursOf(detail), source: 'HIRA' });
}

for (const row of table(pharmacyPath)) {
  if (row['시도코드명'] !== '부산') continue;
  const lat = coord(row['좌표(Y)']);
  const lng = coord(row['좌표(X)']);
  if (lat === null || lng === null) continue;
  places.push({ kind: 'PHARMACY', type: '약국', name: row['요양기관명'], address: row['주소'] || null, phone: phoneOf(row['전화번호']), lat, lng, emergency: false, hours: hoursOf(details.get(row['암호화요양기호'])), source: 'HIRA' });
}

// 경찰 — OSM. 기본 이름에 한글이 없고 한국어 이름에 한글이 있으면 한국어 이름(서버 OsmPoiReader.nameOf 와 같은 규칙)
const hangul = (s) => typeof s === 'string' && /[가-힣]/.test(s);
const seenPolice = new Set();
for (const line of fs.readFileSync(osmPath, 'utf8').split('\n')) {
  if (!line.trim()) continue;
  const node = JSON.parse(line);
  const t = node.tags || {};
  if (t.amenity !== 'police') continue;
  const name = !hangul(t.name) && hangul(t['name:ko']) ? t['name:ko'] : t.name || t['name:ko'];
  if (!name) continue;
  const key = `${name}|${node.lat.toFixed(4)}|${node.lon.toFixed(4)}`;
  if (seenPolice.has(key)) continue;
  seenPolice.add(key);
  places.push({ kind: 'POLICE', type: '경찰', name, nameEn: t['name:en'] || null, address: null, phone: phoneOf(t.phone || t['contact:phone']), lat: coord(node.lat), lng: coord(node.lon), emergency: false, hours: null, source: 'OSM' });
}

places.sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name, 'ko'));
const counts = places.reduce((acc, p) => { acc[p.kind] = (acc[p.kind] || 0) + 1; return acc; }, {});
const doc = {
  source: '병원·의원·약국: 건강보험심사평가원 「전국 병의원 및 약국 현황」 2026년 6월 말 기준(공공누리 제1유형·출처표시). 경찰: © OpenStreetMap 기여자(ODbL 1.0). 요양·정신병원, 치과, 한의원·한방병원, 피부·성형·미용 의원은 뺐다.',
  basedOn: '2026-06-30',
  counts,
  places,
};
fs.writeFileSync(out, JSON.stringify(doc));
console.log(counts, 'withHours', places.filter((p) => p.hours).length, 'er', places.filter((p) => p.emergency).length, fs.statSync(out).size);
