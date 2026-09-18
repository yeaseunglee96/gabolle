/**
 * 화면이 밟고 서는 고도 타일에서 **가짜 능선을 걷어내 다시 굽는다.** (S15P21E201-805)
 *
 *   node dem-clean-tiles.mjs              전부 굽는다 (이미 있는 것은 건너뛴다)
 *   node dem-clean-tiles.mjs --force      있어도 다시 굽는다
 *   node dem-clean-tiles.mjs --probe 35.15395,129.14485 …   한 지점의 높이를 원본/정리본으로 나란히 본다
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 왜 이 파일이 필요한가 — **화면과 경사가 서로 다른 땅을 보고 있었다**
 *
 * `bigData/process/dem-clean.mjs` 가 가짜 혹을 걷어내는 일을 이미 한다. 그런데
 * 그것을 쓰는 곳은 `slope.mjs`·`calibrate-slope.mjs` 뿐이고, **그 둘은 타일을
 * 읽는 순간 메모리에서만 청소한다.** 디스크의 타일은 원본 그대로다.
 *
 * 화면(`public/index.html`)은 그 **디스크의 타일**을 그대로 읽는다:
 *
 *     type: 'raster-dem', tiles: [DATA + '/dem/{z}/{x}_{y}.png'], encoding: 'terrarium'
 *
 * 그래서 **경사 계산에는 없는 능선이 화면에는 그대로 서 있었다.** 마린시티 앞
 * 방파제(35.15395, 129.14485)에 48.8 m 짜리 능선이 그것이다 — 실제로는 평지다.
 * 같은 자료를 봐야 할 둘이 다른 것을 보고 있으면, 어느 쪽이 맞는지 아무도 모른다.
 *
 * 이 파일이 그 간극을 메운다. `dem-clean.mjs` 를 **디스크에 한 번 적용해서**
 * 정리본 타일을 따로 굽고, 화면·축소본·배포 꾸러미가 그것을 읽게 한다.
 * 청소 코드는 **한 벌뿐**이다 — 여기서 베끼지 않고 그대로 부른다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 원본을 덮어쓰지 않는다
 *
 *   원본   bigData/data/raw/dem/15/{x}_{y}.png     ← 손대지 않는다
 *   정리본 bigData/data/clean/dem/15/{x}_{y}.png   ← 이 파일이 만든다
 *
 * 둘 다 있어야 "청소가 무엇을 바꿨나" 를 나란히 볼 수 있다. 읽는 쪽
 * (`serve.mjs` · `build-dist.mjs` · `night.mjs`)에 `--raw` 를 주면 원본으로 돌아간다.
 * 둘 다 `bigData/data/` 안이라 `.gitignore` 대상이다 — 타일은 커밋하지 않는다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 값 범위 검사 — 원본 타일 자체가 망가져 있다
 *
 * 실측(3,127장 전수): **175장에 20,589 픽셀**이 −22,782 ~ −500 m 다.
 * 우리 디코더 탓이 아니라 **AWS 원본 파일에 들어 있는 손상**이다
 * (같은 타일을 PIL 로 따로 풀어 대조했다 — `dem-clean.mjs` 주석에 근거가 있다).
 *
 * `dem-clean.mjs` 가 그것을 메우지만, 여기서 **한 번 더 센다.** 이유는 둘이다.
 *   ① 청소 전 원본이 얼마나 망가져 있었는지가 **보고할 숫자**다
 *   ② 청소 뒤에도 범위 밖이 남으면 그건 **청소가 고장 났다는 뜻**이다.
 *      그때는 조용히 굽지 않고 **멈춘다** — 망가진 타일을 화면에 올리는 것보다
 *      아무것도 안 올리는 편이 낫다
 */
import fs from 'node:fs';
import path from 'node:path';
import { PNG } from 'pngjs';
import { cleanTile, DEM_MIN_M, DEM_MAX_M } from '../bigData/process/dem-clean.mjs';
import { decodePNG, terrariumToElevation } from '../bigData/process/png.mjs';

const HERE = import.meta.dirname;
const ROOT = path.resolve(HERE, '..');
const ZOOM = 15;
const TILE = 256;

export const RAW_DIR = path.join(ROOT, 'bigData/data/raw/dem', String(ZOOM));
export const CLEAN_DIR = path.join(ROOT, 'bigData/data/clean/dem', String(ZOOM));

const args = process.argv.slice(2);
const FORCE = args.includes('--force');

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

// ── 원본 타일 읽기 ────────────────────────────────────────────────────────
// `cleanTile` 은 이음매를 맞추려고 **옆 타일까지** 달라고 한다(halo). 한 장을 구울
// 때마다 이웃 8장을 다시 푸는 셈이라, 캐시가 없으면 같은 파일을 9번 푼다.
const cache = new Map();
function rawTile(tx, ty) {
  const k = `${tx}_${ty}`;
  if (cache.has(k)) return cache.get(k);
  const p = path.join(RAW_DIR, `${k}.png`);
  let v = null;
  if (fs.existsSync(p)) v = terrariumToElevation(decodePNG(fs.readFileSync(p)));
  if (cache.size > 600) for (const key of [...cache.keys()].slice(0, 300)) cache.delete(key);
  cache.set(k, v);
  return v;
}

// ── terrarium 로 되돌리기 ─────────────────────────────────────────────────
/**
 * 고도(m) → RGB. 푸는 식이 `(R*256 + G + B/256) − 32768` 이므로,
 * **1/256 m 단위 정수 하나**로 바꿔 놓고 세 바이트로 쪼개면 반올림 오차가 없다.
 *   u = (elev + 32768) * 256  →  R = u>>16, G = u>>8, B = u
 * 담을 수 있는 범위는 −32768 ~ +32767.996 m 이고, 우리가 쓰는 [−500, 2000] 은
 * 그 안에 넉넉히 들어간다. 남는 오차는 **1/256 m ≈ 3.9 mm** 뿐이다.
 */
function encodeTerrarium(elev) {
  const png = new PNG({ width: TILE, height: TILE });
  for (let i = 0; i < elev.length; i++) {
    let u = Math.round((elev[i] + 32768) * 256);
    if (u < 0) u = 0;
    if (u > 0xffffff) u = 0xffffff;
    const d = i * 4;
    png.data[d] = (u >> 16) & 0xff;
    png.data[d + 1] = (u >> 8) & 0xff;
    png.data[d + 2] = u & 0xff;
    png.data[d + 3] = 255;
  }
  return PNG.sync.write(png);
}

// ── 한 지점의 높이를 원본/정리본으로 나란히 본다 ─────────────────────────
// 화면을 눈으로 보기 전에 **숫자로 먼저 확인하는 자리**다. 진짜 언덕(동백섬)이
// 낮아지지 않았는지는 이 숫자가 먼저 말해 준다.
const WORLD = 2 ** ZOOM * TILE;
const gx = (lon) => ((lon + 180) / 360) * WORLD;
const gy = (lat) => {
  const r = (lat * Math.PI) / 180;
  return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * WORLD;
};
// 되돌리기 — 누른 혹의 자리를 사람이 지도에서 찾아볼 수 있게 위경도로 적는다
const lonOf = (X) => (X / WORLD) * 360 - 180;
const latOf = (Y) => {
  const n = Math.PI - (2 * Math.PI * Y) / WORLD;
  return (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
};

function probe(lat, lon) {
  const X = gx(lon), Y = gy(lat);
  const tx = Math.floor(X / TILE), ty = Math.floor(Y / TILE);
  const ix = Math.min(TILE - 1, Math.max(0, Math.floor(X) - tx * TILE));
  const iy = Math.min(TILE - 1, Math.max(0, Math.floor(Y) - ty * TILE));
  const raw = rawTile(tx, ty);
  const cleanPath = path.join(CLEAN_DIR, `${tx}_${ty}.png`);
  const clean = fs.existsSync(cleanPath)
    ? terrariumToElevation(decodePNG(fs.readFileSync(cleanPath)))
    : null;
  return {
    tile: `${tx}_${ty}`, px: `${ix},${iy}`,
    raw: raw ? raw[iy * TILE + ix] : null,
    clean: clean ? clean[iy * TILE + ix] : null,
  };
}

// ── 본체 ──────────────────────────────────────────────────────────────────
function bake() {
  if (!fs.existsSync(RAW_DIR)) {
    console.error(`🔴 원본 타일이 없습니다: ${path.relative(ROOT, RAW_DIR)}`);
    console.error('   먼저 받으세요:  cd bigData && node collect/terrain.mjs');
    process.exit(1);
  }
  fs.mkdirSync(CLEAN_DIR, { recursive: true });

  const names = fs.readdirSync(RAW_DIR).filter((n) => /^\d+_\d+\.png$/.test(n));
  log(`원본 ${names.length.toLocaleString()}장 → ${path.relative(ROOT, CLEAN_DIR)}`);

  const s = {
    tiles: 0, skipped: 0,
    rawOutOfRangePx: 0, rawOutOfRangeTiles: 0, rawMin: Infinity, rawMax: -Infinity,
    repairedPx: 0, pressedPx: 0, bumps: [],
    cleanMin: Infinity, cleanMax: -Infinity, cleanOutOfRangePx: 0,
  };
  const t0 = Date.now();

  for (const name of names) {
    const [tx, ty] = name.replace('.png', '').split('_').map(Number);
    const dest = path.join(CLEAN_DIR, name);
    if (!FORCE && fs.existsSync(dest)) { s.skipped++; continue; }

    // ① 청소 **전** 원본이 얼마나 망가져 있었나 — 보고할 숫자다
    const before = rawTile(tx, ty);
    if (!before) continue;
    let bad = 0;
    for (let i = 0; i < before.length; i++) {
      const v = before[i];
      if (v < s.rawMin) s.rawMin = v;
      if (v > s.rawMax) s.rawMax = v;
      if (!(v >= DEM_MIN_M && v <= DEM_MAX_M)) bad++;
    }
    if (bad) { s.rawOutOfRangePx += bad; s.rawOutOfRangeTiles++; }

    // ② 청소 — 코드는 bigData/process/dem-clean.mjs 한 벌뿐이다. 문턱도 거기 것을 그대로 쓴다
    //    (열림 반지름 12 px · 튐 25 m · 저지대 15 m — 동백섬이 안 걸리도록 맞춘 값이다)
    const r = cleanTile(rawTile, tx, ty, { tileSize: TILE });
    if (!r) continue;

    // ③ 청소 **뒤** 범위 밖이 남았다면 청소가 고장 난 것이다 — 굽지 않고 멈춘다
    for (let i = 0; i < r.elev.length; i++) {
      const v = r.elev[i];
      if (v < s.cleanMin) s.cleanMin = v;
      if (v > s.cleanMax) s.cleanMax = v;
      if (!(v >= DEM_MIN_M && v <= DEM_MAX_M)) s.cleanOutOfRangePx++;
    }
    if (s.cleanOutOfRangePx) {
      console.error(`🔴 청소 뒤에도 범위 밖 픽셀이 ${s.cleanOutOfRangePx}개 남았습니다 (타일 ${name}).`);
      console.error('   dem-clean.mjs 가 고장 났다는 뜻입니다. 망가진 타일을 화면에 올리지 않고 멈춥니다.');
      process.exit(1);
    }

    fs.writeFileSync(dest, encodeTerrarium(r.elev));
    s.tiles++;
    s.repairedPx += r.repaired;
    s.pressedPx += r.pressed;
    for (const b of r.bumps) s.bumps.push({ ...b, tile: name, elev: +b.peak.toFixed(1), rise: +b.rise.toFixed(1) });
    if (s.tiles % 250 === 0) log(`  ${s.tiles.toLocaleString()} / ${names.length.toLocaleString()}`);
  }

  const secs = (Date.now() - t0) / 1000;

  // 🔴 한 장도 안 구웠으면 **여기서 끝낸다.** 아래 메모를 그대로 쓰면 아무것도
  //    안 재고 나온 Infinity·0 이 지난번 실측을 덮어쓴다. 낡은 기록보다 나쁜 것이
  //    **없던 일을 적어 둔 기록**이다.
  if (s.tiles === 0) {
    log(`구운 것 없음 · 이미 있음 ${s.skipped.toLocaleString()}장 — 다시 구우려면 --force`);
    log(`지난번 기록은 그대로 둡니다: ${path.relative(ROOT, path.join(path.dirname(CLEAN_DIR), '_meta.json'))}`);
    return;
  }

  const meta = {
    at: new Date().toISOString(), zoom: ZOOM, seconds: +secs.toFixed(1),
    source: path.relative(ROOT, RAW_DIR).replace(/\\/g, '/'),
    cleaner: 'bigData/process/dem-clean.mjs (열림 12px · 튐 25m · 저지대 15m)',
    encoding: 'terrarium: elev_m = (R*256 + G + B/256) - 32768',
    tilesWritten: s.tiles, tilesSkipped: s.skipped,
    rangeCheck: {
      window: [DEM_MIN_M, DEM_MAX_M],
      rawOutOfRangePx: s.rawOutOfRangePx, rawOutOfRangeTiles: s.rawOutOfRangeTiles,
      rawMinM: +s.rawMin.toFixed(1), rawMaxM: +s.rawMax.toFixed(1),
      cleanOutOfRangePx: s.cleanOutOfRangePx,
      cleanMinM: +s.cleanMin.toFixed(1), cleanMaxM: +s.cleanMax.toFixed(1),
    },
    repairedPx: s.repairedPx, pressedPx: s.pressedPx, bumps: s.bumps.length,
    // 누른 것을 **전부** 적는다. 개수만 적으면 "동백섬이 여기 들어 있나" 를
    // 확인할 방법이 없고, 확인할 수 없는 필터는 믿을 수 없는 필터다.
    // gx·gy 는 z15 전역 픽셀 좌표 — 위경도로 되돌리는 식은 slope.mjs 의 gx()/gy() 의 역이다.
    bumpList: s.bumps.sort((a, b) => b.rise - a.rise).map((b) => ({
      lat: +latOf(b.gy + 0.5).toFixed(5), lon: +lonOf(b.gx + 0.5).toFixed(5),
      elevM: b.elev, riseM: b.rise, bgM: +b.bg.toFixed(1), px: b.px, tile: b.tile,
    })),
    caveat: '저지대의 폭 98m 미만·높이 25m 초과인 진짜 둔덕은 같이 지워진다. 근거는 dem-clean.mjs 주석.',
  };
  fs.writeFileSync(path.join(path.dirname(CLEAN_DIR), '_meta.json'), JSON.stringify(meta, null, 2));

  log(`구움 ${s.tiles.toLocaleString()}장 · 건너뜀 ${s.skipped.toLocaleString()}장 · ${secs.toFixed(1)}초`);
  log(`값 범위 — 원본에서 [${DEM_MIN_M}, ${DEM_MAX_M}] 밖 ${s.rawOutOfRangePx.toLocaleString()}px / ${s.rawOutOfRangeTiles}장 (최저 ${s.rawMin.toFixed(0)}m)`);
  log(`         정리본 ${s.cleanMin.toFixed(1)} ~ ${s.cleanMax.toFixed(1)} m · 범위 밖 ${s.cleanOutOfRangePx}px`);
  log(`가짜 혹 ${s.bumps.length}곳 (${s.pressedPx.toLocaleString()}px) 누름 · 손상 ${s.repairedPx.toLocaleString()}px 메움`);
  if (s.skipped) log(`🔴 ${s.skipped.toLocaleString()}장은 이미 있어서 건너뛰었습니다 — 위 숫자는 **구운 것만** 센 것입니다. 전수는 --force`);
}

// ── 실행 ──────────────────────────────────────────────────────────────────
if (args.includes('--probe')) {
  const pts = args.slice(args.indexOf('--probe') + 1).filter((a) => /^-?\d/.test(a));
  if (!pts.length) { console.error('사용법: --probe 35.15395,129.14485 [35.15367,129.15175 …]'); process.exit(1); }
  console.log('  위도,경도                 원본        정리본      차이');
  for (const p of pts) {
    const [lat, lon] = p.split(',').map(Number);
    const r = probe(lat, lon);
    const d = r.raw != null && r.clean != null ? (r.clean - r.raw) : null;
    console.log(
      `  ${p.padEnd(24)} ${(r.raw?.toFixed(1) ?? '없음').padStart(8)} m ${(r.clean?.toFixed(1) ?? '없음').padStart(10)} m ` +
      `${(d == null ? '' : (d >= 0 ? '+' : '') + d.toFixed(1) + ' m').padStart(10)}   (타일 ${r.tile} 픽셀 ${r.px})`
    );
  }
} else {
  bake();
}
