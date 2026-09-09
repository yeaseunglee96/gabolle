// 해운대 한 구역의 건물만 뽑아 GeoJSON 으로 낸다.
//
// 입력: bigData/data/raw/building/gis-building.ndjson  (부산 전역 472,608 동)
// 출력: work/haeundae.geojson
//
// 높이는 이렇게 정한다.
//   1) 원본에 실제 높이(heightTag)가 있으면 그것을 쓴다
//   2) 없으면 (지상층수 × 층당 높이) 로 채운다.
//      층당 높이는 bigData 가 이미 구해 둔 값을 쓴다 — 용도별 · 층수 구간별.
//   3) 층수도 없으면 1층으로 본다 — 다만 "추정" 이라고 표시한다.
//      버리면 도시에 구멍이 뚫려서 화면이 거짓말을 한다. 그렇다고 높게 잡으면
//      bigData 가 그림자에서 피하려던 방향(과대평가)으로 간다. 그래서 가장 낮게 잡는다.
//      손실이 비대칭이라는 것은 _height-calibration.json 이 이미 적어 둔 판단이다.

import fs from 'node:fs';
import readline from 'node:readline';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const SRC = path.join(ROOT, 'bigData/data/raw/building/gis-building.ndjson');
const CALIB = path.join(ROOT, 'bigData/data/staged/_height-calibration.json');
const OUT_DIR = path.join(import.meta.dirname, 'work');
const OUT = path.join(OUT_DIR, 'haeundae.geojson');

// 마린시티의 고층 밀집 + 해운대해수욕장 + 달맞이언덕. 약 4.4 × 5 km
const BBOX = { south: 35.140, west: 129.135, north: 35.180, east: 129.190 };

const calib = JSON.parse(fs.readFileSync(CALIB, 'utf8'));
const byLevels = calib.recommendByLevels;

function band(levels) {
  if (levels <= 3) return String(levels);
  if (levels <= 5) return '4-5';
  return '6+';
}

function metersPerLevel(useName, levels) {
  const b = band(levels);
  return byLevels[useName]?.[b] ?? byLevels._default_[b] ?? byLevels._default_['6+'];
}

const stats = {
  seen: 0, inBox: 0, kept: 0,
  fromHeightTag: 0, fromLevels: 0, assumedOneFloor: 0,
  maxHeightM: 0,
};
// 1층으로 가정한 건물이 실제로 작은지 확인하려고 바닥면적을 모아 둔다.
// 큰 건물을 1층으로 세우면 화면에서 바로 티가 난다.
const assumedAreas = [];

fs.mkdirSync(OUT_DIR, { recursive: true });
const out = fs.createWriteStream(OUT, { encoding: 'utf8' });
out.write('{"type":"FeatureCollection","features":[\n');
let first = true;

const rl = readline.createInterface({
  input: fs.createReadStream(SRC, { encoding: 'utf8' }),
  crlfDelay: Infinity,
});

for await (const line of rl) {
  if (!line) continue;
  stats.seen++;
  const b = JSON.parse(line);
  const ring = b.ring;
  if (!ring || ring.length < 4) continue;

  // ring 은 [위도, 경도] 순서다. GeoJSON 은 [경도, 위도] 라서 뒤집는다.
  // bbox 판정은 원본 문서와 같은 규칙 — 점 하나라도 안에 들어오면 포함.
  let hit = false;
  for (const [lat, lon] of ring) {
    if (lat >= BBOX.south && lat <= BBOX.north && lon >= BBOX.west && lon <= BBOX.east) {
      hit = true;
      break;
    }
  }
  if (!hit) continue;
  stats.inBox++;

  let height = null;
  let est = 0;
  if (b.heightTag > 0) {
    height = b.heightTag;
    stats.fromHeightTag++;
  } else if (b.levels > 0) {
    height = +(b.levels * metersPerLevel(b.useName, b.levels)).toFixed(1);
    stats.fromLevels++;
  } else {
    height = byLevels._default_['1'];
    est = 1;
    stats.assumedOneFloor++;
    assumedAreas.push(b.areaM2 ?? 0);
  }
  if (height > stats.maxHeightM) stats.maxHeightM = height;

  const coords = ring.map(([lat, lon]) => [lon, lat]);
  const f = {
    type: 'Feature',
    properties: { h: height, use: b.useName ?? null, lv: b.levels ?? null, est },
    geometry: { type: 'Polygon', coordinates: [coords] },
  };
  out.write((first ? '' : ',\n') + JSON.stringify(f));
  first = false;
  stats.kept++;
}

out.write('\n]}\n');
await new Promise((r) => out.end(r));

assumedAreas.sort((a, b) => a - b);
const pct = (p) => assumedAreas.length ? +assumedAreas[Math.floor((assumedAreas.length - 1) * p)].toFixed(1) : null;
stats.assumedAreaM2 = { p50: pct(0.5), p90: pct(0.9), p99: pct(0.99), max: pct(1) };

const bytes = fs.statSync(OUT).size;
const summary = { at: new Date().toISOString(), bbox: BBOX, source: SRC, out: OUT, outBytes: bytes, stats };
fs.writeFileSync(path.join(OUT_DIR, '_extract.json'), JSON.stringify(summary, null, 2), 'utf8');
console.log(JSON.stringify(summary, null, 2));
