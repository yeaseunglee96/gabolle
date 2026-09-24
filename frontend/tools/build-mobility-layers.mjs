// 경사·그늘 지도 옵션의 자료를 여행 범위 여섯 곳으로 잘라 만든다 (S15P21E201-1569).
//
// 사용법: node tools/build-mobility-layers.mjs <bigData/data 폴더>
//   읽는 것 — staged/segment-slope.ndjson · staged/segment-shadow.ndjson (구간별 값, OSM 길 번호로 묶임)
//             raw/pbf/{road,walk,stairs}.ndjson (길 번호 → 선 모양)
//   쓰는 것 — public/layers/<지역코드>.json. 웹은 같은 주소에서, 앱은 API 주소(=같은 사이트)에서 받는다.
//
// 🔴 기준은 지어내지 않는다 — bigData/config/ontology.jsonld 가 정한 것만 쓴다.
//   · 경사: 선은 합의된 하나뿐 — 휠체어 경사로 기준 1:12 = 8.33%(bm:maxSlopeWheelchair). 유모차·고령자 선은 온톨로지가
//     「미정」이라 쓰지 않는다.
//     🔴 값은 온톨로지의 대표값 p90 이 아니라 **중앙값(bm:slopeP50) · 30m 이상 구간**이다(사용자 결정 2026-09-24).
//     p90 으로 칠하면 평지인 해운대 해변가 길의 43%·서면의 32% 가 「가파름」이 됐다 — 고층 건물 사이에서 고도 자료가
//     튀어 짧은 조각(표본 한두 개)과 긴 길의 일부 표본이 선을 넘는다. 중앙값·30m 이상으로 평지 6~10%, 언덕 35~69%
//     (반경 250m 길이 기준 실측). 온톨로지도 「p50 과 p90 을 견주면 전체가 오르막인지 한쪽만 급한지 안다」고 적는다.
//   · 그늘: 합친 그늘 점수(bm:shadeScore)는 온톨로지가 「미수집 · 시각의 함수」라 정하지 않았다. 여기서는 모든 구간에
//     계산된 **건물 그림자**의 하루 평균(9–18시, 7월 15일)만 싣는다. 가로수는 구간의 5% 에만 붙어 있어 섞지 않는다
// 🔴 원천 자료는 저장소에 없다(bigData/.gitignore). 만든 날의 원천 날짜·개수를 파일에 같이 적는다 — 그래야 낡은 것을 안다.
import { createReadStream, mkdirSync, statSync, writeFileSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { join } from 'node:path';

const DATA = process.argv[2];
if (!DATA) { console.error('사용법: node tools/build-mobility-layers.mjs <bigData/data 폴더>'); process.exit(2); }
const OUT = join(process.cwd(), 'public', 'layers');

// 백엔드 TravelArea 와 같은 중심·반경. 반경에 1km 를 더 얹는다 — 여행 범위 끝의 장소도 그 둘레 길이 보이게.
const AREAS = {
  HAEUNDAE: [35.1587, 129.1604, 2500], GWANGALLI: [35.1532, 129.1186, 2000], NAMPO: [35.0980, 129.0306, 2000],
  SEOMYEON: [35.1578, 129.0594, 2000], YEONGDO: [35.0911, 129.0682, 3000], SONGJEONG: [35.1786, 129.1996, 2000],
};
const MARGIN_M = 1000;

function meters(aLat, aLng, bLat, bLng) {
  const r = (d) => (d * Math.PI) / 180;
  const h = Math.sin(r(bLat - aLat) / 2) ** 2 + Math.cos(r(aLat)) * Math.cos(r(bLat)) * Math.sin(r(bLng - aLng) / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

async function eachLine(file, fn) {
  const rl = createInterface({ input: createReadStream(file, 'utf8'), crlfDelay: Infinity });
  for await (const line of rl) if (line.trim()) fn(JSON.parse(line));
}

const MIN_LENGTH_M = 30;
const slope = new Map();
await eachLine(join(DATA, 'staged', 'segment-slope.ndjson'), (r) => {
  if (r.p50Slope != null && (r.length ?? 0) >= MIN_LENGTH_M) slope.set(r.id, r.p50Slope);
});
const shadow = new Map();
await eachLine(join(DATA, 'staged', 'segment-shadow.ndjson'), (r) => { if (r.meanShadowRatio != null) shadow.set(r.id, r.meanShadowRatio); });

const byArea = Object.fromEntries(Object.keys(AREAS).map((code) => [code, []]));
for (const topic of ['road', 'walk', 'stairs']) {
  await eachLine(join(DATA, 'raw', 'pbf', `${topic}.ndjson`), (way) => {
    const s = slope.get(way.id);
    const b = shadow.get(way.id);
    if ((s == null && b == null) || !Array.isArray(way.geometry) || way.geometry.length < 2) return;
    // 🔴 다리·터널은 뺀다 — 길의 높이와 땅(고도 자료)의 높이가 달라 경사가 터무니없이 나온다(온톨로지 bm:slopeMax 주석:
    //    「교량 구간에서 100% 경사가 관측된다」). 대개 사람이 걷는 길도 아니다.
    const tags = way.tags ?? {};
    if ((tags.bridge && tags.bridge !== 'no') || (tags.tunnel && tags.tunnel !== 'no')) return;
    for (const [code, [lat, lng, radius]] of Object.entries(AREAS)) {
      if (!way.geometry.some((p) => meters(lat, lng, p.lat, p.lon) <= radius + MARGIN_M)) continue;
      const line = [];
      for (const p of way.geometry) line.push(Math.round(p.lon * 1e5) / 1e5, Math.round(p.lat * 1e5) / 1e5);
      // s: 경사 중앙값을 천분율 정수로(83 = 8.3%, 30m 미만은 null) · b: 건물 그림자 하루 평균을 백분율 정수로 · 모르면 null
      byArea[code].push([s == null ? null : Math.round(s * 1000), b == null ? null : Math.round(b * 100), line]);
    }
  });
}

const source = (f) => { const st = statSync(join(DATA, f)); return { file: f, modified: st.mtime.toISOString().slice(0, 10) }; };
mkdirSync(OUT, { recursive: true });
for (const [code, segs] of Object.entries(byArea)) {
  const body = {
    v: 1, area: code, builtAt: new Date().toISOString().slice(0, 10),
    source: [source('staged/segment-slope.ndjson'), source('staged/segment-shadow.ndjson'), source('raw/pbf/walk.ndjson')],
    // 온톨로지에서 온 선 — 앱이 이 값을 그대로 쓴다. 앱에 숫자를 따로 박지 않는다.
    steepPermille: 83,
    slopeBasis: '경사 중앙값 · 30m 이상 길 · 고도 자료로 잰 추정치',
    shadowBasis: '건물 그림자 · 2026-07-15 · 9–18시 평균',
    segs,
  };
  const file = join(OUT, `${code}.json`);
  writeFileSync(file, JSON.stringify(body));
  const steep = segs.filter((x) => x[0] != null && x[0] >= 83).length;
  console.log(`${code}\t구간 ${segs.length}\t가파름 ${steep}\t${(statSync(file).size / 1024).toFixed(0)}KB`);
}
