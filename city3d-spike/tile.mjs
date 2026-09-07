// 해운대 건물 GeoJSON 을 "타일" 로 굽는다.
//
// 타일 = 지도를 격자로 잘라 놓은 조각. 화면에 보이는 조각만 내려받으면 되므로
// 건물이 몇 만 개여도 한 번에 오는 양은 작다. 이게 이 실험의 핵심 가정이다.
//
// 굽는 것은 한 번뿐이다. 부산은 안 변하므로 서버가 실시간으로 만들 이유가 없다.

import fs from 'node:fs';
import path from 'node:path';
import geojsonvt from 'geojson-vt';
import vtpbf from 'vt-pbf';

const HERE = import.meta.dirname;
const SRC = path.join(HERE, 'work/haeundae.geojson');
const OUT_DIR = path.join(HERE, 'public/tiles');

const BBOX = { south: 35.140, west: 129.135, north: 35.180, east: 129.190 };
const MIN_Z = 12;
const MAX_Z = 16;

const lonToX = (lon, z) => Math.floor(((lon + 180) / 360) * 2 ** z);
const latToY = (lat, z) => {
  const r = (lat * Math.PI) / 180;
  return Math.floor(((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** z);
};

const geojson = JSON.parse(fs.readFileSync(SRC, 'utf8'));
console.log(`건물 ${geojson.features.length.toLocaleString()} 동을 읽었습니다.`);

const index = new geojsonvt(geojson, {
  maxZoom: MAX_Z,
  indexMaxZoom: MAX_Z,
  tolerance: 3,   // 줌이 낮을 때 건물 윤곽을 얼마나 단순하게 만들지
  extent: 4096,
  buffer: 64,     // 타일 경계에서 건물이 잘려 보이지 않게 조금 넘겨 담는다
});

fs.rmSync(OUT_DIR, { recursive: true, force: true });

const perZoom = [];
let total = 0, totalBytes = 0, maxTileBytes = 0, maxTileAt = null;

for (let z = MIN_Z; z <= MAX_Z; z++) {
  const x0 = lonToX(BBOX.west, z), x1 = lonToX(BBOX.east, z);
  const y0 = latToY(BBOX.north, z), y1 = latToY(BBOX.south, z);
  let count = 0, bytes = 0;

  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const tile = index.getTile(z, x, y);
      if (!tile || tile.features.length === 0) continue;

      // 레이어 이름 'building' 은 화면 쪽 스타일이 부르는 이름과 같아야 한다.
      const buf = Buffer.from(vtpbf.fromGeojsonVt({ building: tile }, { version: 2 }));
      const dir = path.join(OUT_DIR, String(z), String(x));
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, `${y}.pbf`), buf);

      count++; bytes += buf.length;
      if (buf.length > maxTileBytes) { maxTileBytes = buf.length; maxTileAt = `${z}/${x}/${y}`; }
    }
  }
  perZoom.push({ z, tiles: count, bytes, avgKB: count ? +(bytes / count / 1024).toFixed(1) : 0 });
  total += count; totalBytes += bytes;
}

const summary = {
  at: new Date().toISOString(),
  buildings: geojson.features.length,
  zoomRange: [MIN_Z, MAX_Z],
  tiles: total,
  totalMB: +(totalBytes / 1024 / 1024).toFixed(2),
  maxTileKB: +(maxTileBytes / 1024).toFixed(1),
  maxTileAt,
  perZoom,
};
fs.writeFileSync(path.join(HERE, 'work/_tile.json'), JSON.stringify(summary, null, 2), 'utf8');
console.log(JSON.stringify(summary, null, 2));
