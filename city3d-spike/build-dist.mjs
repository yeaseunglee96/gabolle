// 서버에 그대로 올릴 수 있는 폴더 하나를 만든다.
//
// 왜 필요한가 — 지금 이 실험은 로컬 서버(serve.mjs)가 세 군데에서 파일을 끌어다 쓴다:
//   화면 파일은 public/ · 지도 라이브러리는 node_modules/ · 땅 높이 15단계는 bigData/
// 정적 서버(nginx)는 그런 짜맞추기를 못 한다. 그래서 한 폴더로 미리 모아 둔다.
//
//   node build-dist.mjs        → dist/ 가 생긴다
//
// dist/ 를 통째로 웹 서버의 아무 폴더에나 두면 된다. 화면 쪽 코드가 자기가 어느 하위
// 폴더에 놓였는지 스스로 알아내므로(basePath), 주소를 코드에 적을 필요가 없다.

import fs from 'node:fs';
import path from 'node:path';

const HERE = import.meta.dirname;
const ROOT = path.resolve(HERE, '..');
const DIST = path.join(HERE, 'dist');

// 🔴 땅 높이는 **정리본**을 담는다 (S15P21E201-805).
//
// serve.mjs 와 **같은 것을 골라야 한다.** 여기서만 원본을 담으면 로컬에서는
// 고쳐진 화면을 보고 서버에서는 가짜 능선이 그대로인 화면이 뜬다 — 로컬에서
// 되던 것이 서버에서만 깨지는, 가장 찾기 어려운 종류의 버그다.
//
//   node build-dist.mjs          정리본 (기본)
//   node build-dist.mjs --raw    원본 — 비교용 꾸러미를 만들 때만
const RAW_DEM = process.argv.includes('--raw');
const DEM15 = path.join(ROOT, RAW_DEM ? 'bigData/data/raw/dem/15' : 'bigData/data/clean/dem/15');

// 정리본이 없으면 **여기서는 멈춘다.** serve.mjs 는 경고만 하고 원본으로 넘어가지만
// (화면을 아예 못 띄우면 곤란하니까), 배포 꾸러미는 다르다 — 조용히 원본이 실려
// 올라가면 "화면만 옛날로 돌아간" 것을 아무도 모른다.
if (!fs.existsSync(DEM15)) {
  console.error(`🔴 땅 높이 타일이 없습니다: ${path.relative(ROOT, DEM15)}`);
  console.error(RAW_DEM
    ? '   먼저 받으세요:  cd ../bigData && node collect/terrain.mjs'
    : '   먼저 구우세요:  node dem-clean-tiles.mjs');
  process.exit(1);
}

// 어디에서 무엇을 가져와 dist 의 어디에 놓을지.
// 로컬 서버(serve.mjs)의 주소 규칙과 **같은 모양**이어야 한다 — 다르면 로컬에서 되던 것이
// 서버에서만 깨진다. 그게 가장 찾기 어려운 종류의 버그다.
const COPY = [
  { from: path.join(HERE, 'public'), to: DIST, skip: ['dem-tiles', 'tiles'] },
  { from: path.join(HERE, 'node_modules/maplibre-gl/dist'), to: path.join(DIST, 'lib'),
    only: /^maplibre-gl(-shared|-worker)?\.(mjs|css)$/ },
  { from: path.join(HERE, 'public/dem-tiles'), to: path.join(DIST, 'dem') },
  { from: DEM15, to: path.join(DIST, 'dem/15') },
];

let files = 0;
let bytes = 0;

function copyDir(from, to, opt = {}) {
  if (!fs.existsSync(from)) {
    console.log(`  건너뜀 (없음): ${path.relative(ROOT, from)}`);
    return;
  }
  fs.mkdirSync(to, { recursive: true });
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    if (opt.skip?.includes(e.name)) continue;
    const src = path.join(from, e.name);
    const dst = path.join(to, e.name);
    if (e.isDirectory()) {
      copyDir(src, dst, { only: opt.only });
    } else {
      if (opt.only && !opt.only.test(e.name)) continue;
      fs.copyFileSync(src, dst);
      files++;
      bytes += fs.statSync(dst).size;
    }
  }
}

fs.rmSync(DIST, { recursive: true, force: true });
console.log('배포 꾸러미를 만듭니다…');
for (const c of COPY) {
  const before = files;
  copyDir(c.from, c.to, c);
  console.log(`  ${path.relative(ROOT, c.from)} → dist/${path.relative(DIST, c.to) || '.'}  (${files - before} 개)`);
}

// 올리기 전에 확인한다. 하나라도 없으면 서버에서 화면이 하얗게 뜨고,
// 그때는 원인을 찾기가 여기서 찾는 것보다 훨씬 어렵다.
const MUST = [
  'index.html', 'sun.mjs', 'bridges.geojson', 'crossings.geojson',
  'lib/maplibre-gl.mjs', 'lib/maplibre-gl.css',
  'tiles-busan/14', 'dem/12', 'dem/15',
];
const missing = MUST.filter((m) => !fs.existsSync(path.join(DIST, m)));

console.log(`\n파일 ${files.toLocaleString()} 개 · ${(bytes / 1048576).toFixed(1)} MB → dist/`);
console.log(RAW_DEM
  ? '🔴 땅 높이: **원본** (--raw) — 매립지의 가짜 능선이 그대로 실렸습니다. 비교용 꾸러미입니다.'
  : '땅 높이: 정리본 (가짜 능선을 걷어낸 것)');
if (missing.length) {
  console.error('빠진 것이 있습니다: ' + missing.join(', '));
  process.exit(1);
}
console.log('필수 파일 확인 완료.');

// 항공사진 인증키. public/config.local.js 는 gitignore 대상이라 **clone 에는 없다** — 있으면 위에서 같이 복사됐다.
// 없어도 화면은 뜬다(항공사진만 꺼진다). 그래서 실패가 아니라 경고다. 하지만 모르고 올리면 서버에서
// "사진이 왜 안 나오지" 를 한참 찾게 되므로 여기서 크게 말한다.
if (!fs.existsSync(path.join(DIST, 'config.local.js'))) {
  console.warn('\n🔴 public/config.local.js 가 없습니다 — 이 배포본은 **항공사진이 꺼진 채** 올라갑니다.');
  console.warn('   config.example.js 를 config.local.js 로 복사하고 브이월드 인증키를 적은 뒤 다시 만드세요.');
}
