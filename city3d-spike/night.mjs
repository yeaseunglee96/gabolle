// 밤새 도는 일괄 작업 — 부산 전역을 3D 로 세울 재료를 전부 굽는다.
//
//   1) 부산 전역 건물 472,608 동을 뽑는다
//   2) 그것을 타일로 굽는다 (확대 12~16 단계)
//   3) 땅 높이 축소본을 굽는다 (확대 11~14 단계) — 지금은 15 단계밖에 없어서
//      멀리 빼면 지형이 안 나온다. 그 구멍을 메우는 일이다.
//
// 한 단계가 실패해도 앞 단계 결과는 남는다. 무엇이 어디서 멈췄는지 전부 기록한다.
// 사람이 자는 동안 도는 작업이라 실패를 숨기면 아침에 원인을 못 찾는다.

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import geojsonvt from 'geojson-vt';
import vtpbf from 'vt-pbf';
import { PNG } from 'pngjs';

const HERE = import.meta.dirname;
const ROOT = path.resolve(HERE, '..');
const WORK = path.join(HERE, 'work');
const LOG = path.join(WORK, 'night.log');

// 부산 전역. bigData 가 쓰는 것과 같은 범위다 (바다·김해·양산 일부 포함).
const BBOX = { south: 34.88, west: 128.74, north: 35.4, east: 129.32 };
const BLD_ZOOMS = [12, 16];
const DEM_SRC_Z = 15;
const DEM_MIN_Z = 11;

// 🔴 땅 높이 축소본의 재료 (S15P21E201-805)
//   --raw-dem   원본 z15 로 줄인다. **비교용이다** — 매립지의 가짜 능선이 그대로 따라온다
//   --dem-only  3단계(땅 높이)만 돈다. 건물 재료(285 MB OSM 추출본)가 없어도 되고,
//               정리본을 다시 구운 뒤 축소본만 맞출 때 쓴다
const RAW_DEM = process.argv.includes('--raw-dem');
const DEM_ONLY = process.argv.includes('--dem-only');

fs.mkdirSync(WORK, { recursive: true });
fs.writeFileSync(LOG, '');
const say = (msg) => {
  const line = `[${new Date().toISOString()}] ${msg}`;
  console.log(line);
  fs.appendFileSync(LOG, line + '\n');
};

const lonToX = (lon, z) => Math.floor(((lon + 180) / 360) * 2 ** z);
const latToY = (lat, z) => {
  const r = (lat * Math.PI) / 180;
  return Math.floor(((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** z);
};

const report = { startedAt: new Date().toISOString(), steps: {}, ok: false };
const finish = (ok) => {
  report.ok = ok;
  report.endedAt = new Date().toISOString();
  report.minutes = +((Date.parse(report.endedAt) - Date.parse(report.startedAt)) / 60000).toFixed(1);
  fs.writeFileSync(path.join(WORK, '_night.json'), JSON.stringify(report, null, 2), 'utf8');
  say(ok ? '전부 끝났습니다.' : '중간에 멈췄습니다. _night.json 을 보세요.');
  process.exit(ok ? 0 : 1);
};

// ─────────────────────────────────────────────────────────────────
// 1) 부산 전역 건물 뽑기
// ─────────────────────────────────────────────────────────────────
async function extractBuildings() {
  const SRC = path.join(ROOT, 'bigData/data/raw/building/gis-building.ndjson');
  const OUT = path.join(WORK, 'busan.geojson');
  const calib = JSON.parse(fs.readFileSync(path.join(ROOT, 'bigData/data/staged/_height-calibration.json'), 'utf8'));
  const byLevels = calib.recommendByLevels;

  const band = (lv) => (lv <= 3 ? String(lv) : lv <= 5 ? '4-5' : '6+');
  const mPerLevel = (use, lv) => byLevels[use]?.[band(lv)] ?? byLevels._default_[band(lv)] ?? byLevels._default_['6+'];

  const s = { seen: 0, kept: 0, fromHeightTag: 0, fromLevels: 0, assumedOneFloor: 0, maxHeightM: 0 };
  const out = fs.createWriteStream(OUT, { encoding: 'utf8' });
  out.write('{"type":"FeatureCollection","features":[\n');
  let first = true;

  const rl = readline.createInterface({
    input: fs.createReadStream(SRC, { encoding: 'utf8' }),
    crlfDelay: Infinity,
  });

  for await (const line of rl) {
    if (!line) continue;
    s.seen++;
    if (s.seen % 100000 === 0) say(`  건물 ${s.seen.toLocaleString()} 줄 읽는 중…`);

    const b = JSON.parse(line);
    const ring = b.ring;
    if (!ring || ring.length < 4) continue;

    let hit = false;
    for (const [lat, lon] of ring) {
      if (lat >= BBOX.south && lat <= BBOX.north && lon >= BBOX.west && lon <= BBOX.east) { hit = true; break; }
    }
    if (!hit) continue;

    let h, est = 0;
    if (b.heightTag > 0) { h = b.heightTag; s.fromHeightTag++; }
    else if (b.levels > 0) { h = +(b.levels * mPerLevel(b.useName, b.levels)).toFixed(1); s.fromLevels++; }
    else { h = byLevels._default_['1']; est = 1; s.assumedOneFloor++; }
    if (h > s.maxHeightM) s.maxHeightM = h;

    const f = {
      type: 'Feature',
      // a = 바닥면적(㎡). 멀리서 볼 때 작은 건물을 걸러내는 데 쓴다.
      properties: { h, a: Math.round(b.areaM2 ?? 0), use: b.useName ?? null, lv: b.levels ?? null, est },
      geometry: { type: 'Polygon', coordinates: [ring.map(([lat, lon]) => [lon, lat])] },
    };
    out.write((first ? '' : ',\n') + JSON.stringify(f));
    first = false;
    s.kept++;
  }

  out.write('\n]}\n');
  await new Promise((r) => out.end(r));
  s.outMB = +(fs.statSync(OUT).size / 1048576).toFixed(1);
  report.steps.extract = s;
  say(`1단계 끝 — 건물 ${s.kept.toLocaleString()} 동, ${s.outMB} MB`);
  return OUT;
}

// ─────────────────────────────────────────────────────────────────
// 2) 건물 타일 굽기
// ─────────────────────────────────────────────────────────────────
// 🔴 멀리서 볼 때는 작은 건물을 그리지 않는다.
//
// 확대 12 단계에서 화면의 한 점은 약 38 m 다. 4×4 m 창고는 애초에 보이지 않는데
// 그것까지 담으면 타일 한 장이 2 MB 를 넘는다 — 에픽의 전송량 기준을 타일 하나가 혼자 깬다.
// 그래서 확대 단계마다 "이 크기 아래는 안 보낸다" 는 문턱을 둔다.
//
// 문턱은 그 단계에서 한 점이 몇 m 인지로 정했다. 높은 건물은 바닥이 작아도 남긴다 —
// 멀리서 도시를 알아보게 하는 것은 스카이라인이기 때문이다.
const TIERS = [
  { zooms: [12, 12], mPerPx: 38, minAreaM2: 1500, minHeightM: 40 },
  { zooms: [13, 13], mPerPx: 19, minAreaM2: 400, minHeightM: 25 },
  { zooms: [14, 14], mPerPx: 9.5, minAreaM2: 100, minHeightM: 12 },
  { zooms: [15, 16], mPerPx: 4.8, minAreaM2: 0, minHeightM: 0 },
];

function tileBuildings(srcPath) {
  const OUT_DIR = path.join(HERE, 'public/tiles-busan');
  say('2단계 — 건물 GeoJSON 을 메모리로 올립니다');
  const all = JSON.parse(fs.readFileSync(srcPath, 'utf8')).features;
  say(`2단계 — ${all.length.toLocaleString()} 동`);

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  const s = { tiles: 0, bytes: 0, maxTileKB: 0, maxTileAt: null, perZoom: [] };

  for (const tier of TIERS) {
    const [zLo, zHi] = tier.zooms;
    const features = tier.minAreaM2 === 0
      ? all
      : all.filter((f) => f.properties.a >= tier.minAreaM2 || f.properties.h >= tier.minHeightM);
    say(`  확대 ${zLo}${zHi > zLo ? '~' + zHi : ''} 단계 — ${features.length.toLocaleString()} 동을 색인합니다 (한 점 약 ${tier.mPerPx} m)`);

    const index = new geojsonvt({ type: 'FeatureCollection', features }, {
      maxZoom: zHi, indexMaxZoom: zHi, tolerance: 3, extent: 4096, buffer: 64,
    });

    for (let z = zLo; z <= zHi; z++) {
      const x0 = lonToX(BBOX.west, z), x1 = lonToX(BBOX.east, z);
      const y0 = latToY(BBOX.north, z), y1 = latToY(BBOX.south, z);
      let count = 0, bytes = 0, maxKB = 0;

      for (let x = x0; x <= x1; x++) {
        for (let y = y0; y <= y1; y++) {
          const tile = index.getTile(z, x, y);
          if (!tile || tile.features.length === 0) continue;
          const buf = Buffer.from(vtpbf.fromGeojsonVt({ building: tile }, { version: 2 }));
          const dir = path.join(OUT_DIR, String(z), String(x));
          fs.mkdirSync(dir, { recursive: true });
          fs.writeFileSync(path.join(dir, `${y}.pbf`), buf);
          count++; bytes += buf.length;
          const kb = buf.length / 1024;
          if (kb > maxKB) maxKB = kb;
          if (kb > s.maxTileKB) { s.maxTileKB = +kb.toFixed(1); s.maxTileAt = `${z}/${x}/${y}`; }
        }
      }
      s.perZoom.push({
        z, buildings: features.length, tiles: count,
        avgKB: count ? +(bytes / count / 1024).toFixed(1) : 0,
        maxKB: +maxKB.toFixed(1),
      });
      s.tiles += count; s.bytes += bytes;
      say(`    → 타일 ${count.toLocaleString()} 장, 평균 ${(bytes / count / 1024).toFixed(1)} KB, 최대 ${maxKB.toFixed(1)} KB`);
    }
  }

  s.totalMB = +(s.bytes / 1048576).toFixed(1);
  report.steps.tiles = s;
  say(`2단계 끝 — 타일 ${s.tiles.toLocaleString()} 장, ${s.totalMB} MB, 가장 큰 타일 ${s.maxTileKB} KB`);
}

// ─────────────────────────────────────────────────────────────────
// 3) 땅 높이 축소본 굽기
// ─────────────────────────────────────────────────────────────────
// terrarium = 픽셀 색 안에 높이(m)를 적어 두는 방식. 값을 평균 내면 색이 깨지므로
// 픽셀을 하나 걸러 하나 고르는 방식(가장 가까운 값)으로 줄인다. 지형의 큰 모양은 남는다.
// 🔴 축소본의 재료는 **정리본 z15** 다 (S15P21E201-805). 원본에서 줄이면 확대 11~14
//    단계에도 가짜 능선이 그대로 따라 내려간다 — 멀리서 보면 사라졌다가 가까이
//    가면 나타나는, 더 나쁜 모양이 된다. serve.mjs · build-dist.mjs 와 같은 것을 본다.
function demOverviews() {
  const SRC_DIR = path.join(ROOT, RAW_DEM ? 'bigData/data/raw/dem' : 'bigData/data/clean/dem', String(DEM_SRC_Z));
  const OUT_DIR = path.join(HERE, 'public/dem-tiles');
  if (!fs.existsSync(SRC_DIR)) {
    throw new Error(`땅 높이 z${DEM_SRC_Z} 타일이 없습니다: ${path.relative(ROOT, SRC_DIR)}`
      + (RAW_DEM ? ' — cd ../bigData && node collect/terrain.mjs' : ' — node dem-clean-tiles.mjs'));
  }
  say(`  재료: ${path.relative(ROOT, SRC_DIR).replace(/\\/g, '/')}${RAW_DEM ? '  🔴 원본 (--raw-dem)' : ''}`);
  const SIZE = 256;
  const SEA = [128, 0, 0]; // 높이 0 m 을 terrarium 으로 적으면 이 색이다

  const readTile = (z, x, y) => {
    const p = z === DEM_SRC_Z ? path.join(SRC_DIR, `${x}_${y}.png`) : path.join(OUT_DIR, String(z), `${x}_${y}.png`);
    if (!fs.existsSync(p)) return null;
    try { return PNG.sync.read(fs.readFileSync(p)); } catch { return null; }
  };

  const s = { perZoom: [], failed: [] };
  fs.rmSync(OUT_DIR, { recursive: true, force: true });

  for (let z = DEM_SRC_Z - 1; z >= DEM_MIN_Z; z--) {
    const x0 = lonToX(BBOX.west, z), x1 = lonToX(BBOX.east, z);
    const y0 = latToY(BBOX.north, z), y1 = latToY(BBOX.south, z);
    const dir = path.join(OUT_DIR, String(z));
    fs.mkdirSync(dir, { recursive: true });
    let count = 0;

    for (let x = x0; x <= x1; x++) {
      for (let y = y0; y <= y1; y++) {
        const kids = [
          [0, 0, readTile(z + 1, x * 2, y * 2)],
          [1, 0, readTile(z + 1, x * 2 + 1, y * 2)],
          [0, 1, readTile(z + 1, x * 2, y * 2 + 1)],
          [1, 1, readTile(z + 1, x * 2 + 1, y * 2 + 1)],
        ];
        if (!kids.some(([, , k]) => k)) continue;

        const out = new PNG({ width: SIZE, height: SIZE });
        for (const [qx, qy, kid] of kids) {
          for (let j = 0; j < SIZE / 2; j++) {
            for (let i = 0; i < SIZE / 2; i++) {
              const di = ((j + qy * (SIZE / 2)) * SIZE + (i + qx * (SIZE / 2))) * 4;
              if (kid) {
                const si = (j * 2 * SIZE + i * 2) * 4;
                out.data[di] = kid.data[si];
                out.data[di + 1] = kid.data[si + 1];
                out.data[di + 2] = kid.data[si + 2];
              } else {
                out.data[di] = SEA[0]; out.data[di + 1] = SEA[1]; out.data[di + 2] = SEA[2];
              }
              out.data[di + 3] = 255;
            }
          }
        }
        fs.writeFileSync(path.join(dir, `${x}_${y}.png`), PNG.sync.write(out));
        count++;
      }
    }
    s.perZoom.push({ z, tiles: count });
    say(`  땅 높이 확대 ${z} 단계 — 타일 ${count.toLocaleString()} 장`);
  }

  report.steps.dem = s;
  say('3단계 끝 — 땅 높이 축소본');
}

// ─────────────────────────────────────────────────────────────────
try {
  say(DEM_ONLY ? '땅 높이 축소본만 굽습니다 (--dem-only)' : '밤샘 작업 시작 — 부산 전역');
  if (!DEM_ONLY) {
    const src = await extractBuildings();
    tileBuildings(src);
  }
  demOverviews();
  finish(true);
} catch (err) {
  report.error = { message: String(err && err.message), stack: String(err && err.stack).slice(0, 2000) };
  say('실패: ' + report.error.message);
  finish(false);
}
