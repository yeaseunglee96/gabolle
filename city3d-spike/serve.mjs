// 실험용 정적 서버. 폰에서 열 수 있게 같은 와이파이 안에서 주소를 열어 준다.
//
// 서버가 하는 일은 파일을 그대로 내주는 것뿐이다. 3D 는 전부 폰 안에서 만들어진다.
// 그래서 이 실험이 통과하면 실제 서비스에서도 서버 부담이 0 이다.

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import zlib from 'node:zlib';

const HERE = import.meta.dirname;
const ROOT = path.resolve(HERE, '..');
const PORT = 8787;

// 🔴 땅 높이는 **정리본**을 내준다 (S15P21E201-805).
//
// 원본 타일에는 매립지·모래해안에 **없는 능선**이 서 있다 — 마린시티 앞
// 방파제의 51.5 m 가 그것이고 실제로는 평지다. 뿌리는 SRTM(2000년 레이더
// 측량)이 물·젖은 모래에서 신호를 잘못 받은 것이다.
// 걷어내는 일은 `bigData/process/dem-clean.mjs` 가 하고, 그것을 디스크에
// 구워 두는 것이 `dem-clean-tiles.mjs` 다. 여기서는 구운 것을 내주기만 한다.
//
//   node serve.mjs          정리본 (기본)
//   node serve.mjs --raw    원본 — **청소가 무엇을 바꿨나를 나란히 볼 때만** 쓴다
const RAW_DEM = process.argv.includes('--raw');
const CLEAN15 = path.join(ROOT, 'bigData/data/clean/dem/15');
const RAW15 = path.join(ROOT, 'bigData/data/raw/dem/15');
// 정리본이 아직 안 구워졌으면 원본으로 내주되 **크게 말한다.** 조용히 원본을
// 내주면 "고쳤는데 화면은 그대로네" 를 몇 시간 찾게 된다.
const cleanMissing = !RAW_DEM && !fs.existsSync(CLEAN15);
const DEM15 = RAW_DEM || cleanMissing ? RAW15 : CLEAN15;

// 주소 앞부분 → 실제 폴더
// 위에서부터 먼저 맞는 것을 쓴다. 땅 높이는 확대 15 단계만 타일 한 장이 그대로고
// 그보다 먼 단계는 우리가 구운 축소본이라 자리가 다르다.
const MOUNTS = [
  ['/tiles-busan/', path.join(HERE, 'public/tiles-busan')],
  ['/tiles/', path.join(HERE, 'public/tiles')],
  ['/dem/15/', DEM15],
  ['/dem/', path.join(HERE, 'public/dem-tiles')],
  ['/lib/', path.join(HERE, 'node_modules/maplibre-gl/dist')],
  // 맨 아래 — 위에서 안 걸린 주소는 public 폴더에서 그대로 찾는다 (bridges.geojson 등)
  ['/', path.join(HERE, 'public')],
];

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.pbf': 'application/x-protobuf',
  '.json': 'application/json; charset=utf-8',
  '.geojson': 'application/geo+json; charset=utf-8',
  '.map': 'application/json',
};
// 이미 압축된 형식(png)은 다시 압축하지 않는다. 시간만 쓰고 안 줄어든다.
const GZIP = new Set(['.pbf', '.js', '.mjs', '.css', '.html', '.json', '.geojson']);

const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  let file = null;

  if (url === '/' || url === '/index.html') {
    file = path.join(HERE, 'public/index.html');
  } else {
    for (const [prefix, dir] of MOUNTS) {
      if (url.startsWith(prefix)) {
        const rel = url.slice(prefix.length);
        const resolved = path.resolve(dir, rel);
        // 마운트 폴더 밖으로 못 나가게 막는다
        if (resolved.startsWith(dir)) file = resolved;
        break;
      }
    }
  }

  if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404).end('not found');
    return;
  }

  const ext = path.extname(file);
  const body = fs.readFileSync(file);
  const headers = { 'Content-Type': TYPES[ext] ?? 'application/octet-stream', 'Cache-Control': 'no-cache' };

  if (GZIP.has(ext) && /\bgzip\b/.test(req.headers['accept-encoding'] ?? '')) {
    const gz = zlib.gzipSync(body);
    res.writeHead(200, { ...headers, 'Content-Encoding': 'gzip', 'Content-Length': gz.length }).end(gz);
  } else {
    res.writeHead(200, { ...headers, 'Content-Length': body.length }).end(body);
  }
});

server.listen(PORT, '0.0.0.0', () => {
  const ips = Object.values(os.networkInterfaces())
    .flat()
    .filter((n) => n && n.family === 'IPv4' && !n.internal)
    .map((n) => n.address);

  console.log('\n  해운대 3D 실험 서버가 떴습니다.\n');
  // 어느 땅을 밟고 서 있는지 매번 적는다. 화면이 이상할 때 3초에 알 수 있어야 한다.
  if (cleanMissing) {
    console.log('  🔴 땅 높이: **원본** — 정리본이 없습니다. 매립지의 가짜 능선이 그대로 보입니다.');
    console.log('     구우려면:  node dem-clean-tiles.mjs   (원본이 없으면 먼저 cd ../bigData && node collect/terrain.mjs)');
  } else if (RAW_DEM) {
    console.log('  🔴 땅 높이: **원본** (--raw) — 가짜 능선이 그대로 있습니다. 비교용입니다.');
  } else {
    console.log('  땅 높이: 정리본 (가짜 능선을 걷어낸 것) — 원본으로 보려면 --raw');
  }
  console.log(`\n  이 PC 에서:  http://localhost:${PORT}`);
  for (const ip of ips) console.log(`  폰에서:      http://${ip}:${PORT}   ← 같은 와이파이에 있어야 합니다`);
  console.log('\n  멈추려면 Ctrl+C\n');
});
