// 지도 맞춤 빠른 확인 — 빌드 없이 앱 지도 HTML(kakaoMapHtml.ts)을 기기 크기 브라우저에 띄워
// «번호 점이 위·아래 가림 띠 밖에 다 보이는가»를 자동으로 판정한다(S15P21E201-1988).
//
//   KAKAO_MAP_JS_KEY=… node tools/map-fit-harness.mjs [출력 폴더]
//
// 카카오 키는 등록된 도메인에서만 열린다 → 운영 주소(https://j15e201.p.ssafy.io/__maptest)로 가로채 띄운다.
// 앱 WebView 도 같은 baseUrl 을 쓴다(RouteMap.native.tsx MAP_BASE_URL). 키는 출력하지 않는다.
import { createRequire } from 'node:module';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const require = createRequire(path.join(root, 'package.json'));
const ts = require('typescript');
const { chromium } = require('playwright');

function load(rel) {
  const src = readFileSync(path.join(root, rel), 'utf8');
  const out = ts.transpileModule(src, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const mod = { exports: {} };
  new Function('module', 'exports', 'require', out)(mod, mod.exports, require);
  return mod.exports;
}
const { buildKakaoMapHtml } = load('src/map/kakaoMapHtml.ts');
const { fitPadding } = load('src/map/mapFocus.ts');

const key = process.env.KAKAO_MAP_JS_KEY;
if (!key) { console.error('KAKAO_MAP_JS_KEY 가 없다'); process.exit(2); }
const outDir = process.argv[2] || path.join(os.tmpdir(), 'gabolle-map-fit');
mkdirSync(outDir, { recursive: true });

// 앱 값: 점(마커) 지름 34~40 → 반지름 20.
const R = 20;
// 기기·화면 상태. h = WebView 높이(폰 판은 화면 높이 + radius.lg*2), top/bottom = 앱이 RouteMap 에 넘기는 topInset/bottomInset.
// 폰 판(TripPageMobile): topInset = 20 + insets.top + 8 + 40 (+ 범례 32), 창을 연 상태 bottomInset = (화면 − (insets.top + min(190, 화면·0.3))) + 20.
function phone(name, w, screenH, insetTop, legend, sheet) {
  const sheetTop = insetTop + Math.min(190, Math.round(screenH * 0.3));
  const covered = sheet === 'open' ? screenH - sheetTop : Math.max(8, 24) + 64 + 8 + 96;
  return { name: `${name}·${sheet}${legend ? '·범례' : ''}`, w, h: screenH + 40, top: 20 + insetTop + 8 + 40 + (legend ? 32 : 0), bottom: covered + 20, sheetOpen: sheet === 'open', collapsedBottom: Math.max(8, 24) + 64 + 8 + 96 + 20 };
}
const devices = [
  phone('폴드접음', 369, 905, 32, false, 'open'),
  phone('폴드접음', 369, 905, 32, true, 'open'),
  phone('폴드접음', 369, 905, 32, false, 'collapsed'),
  phone('S24+', 384, 832, 32, false, 'open'),
  phone('S24+', 384, 832, 32, true, 'collapsed'),
  // 넓은 판(TripPageDesktop) 큰 지도: 위에 「장소 N곳」 칩(top 12, 높이 34) — 범례가 있으면 그 아래까지 80.
  { name: '탭가로·큰지도', w: 760, h: 600, top: 54, bottom: 0 },
  { name: '탭가로·큰지도·범례', w: 760, h: 600, top: 80, bottom: 0 },
  { name: '탭세로·큰지도', w: 760, h: 900, top: 54, bottom: 0 },
];
const trips = {
  '영도(10/10)': [[35.0785, 129.0453], [35.0517, 129.0870], [35.0785, 129.0800], [35.0955, 129.0365], [35.0898, 129.0388], [35.0980, 129.0280]],
  '넓게': [[35.1587, 129.1604], [35.0975, 129.0106], [35.2445, 129.2222], [35.1578, 129.0600], [35.0469, 128.9665]],
  '한동네': [[35.1587, 129.1604], [35.1631, 129.1636], [35.1600, 129.1700], [35.1555, 129.1520], [35.1690, 129.1750]],
  '일직선': [[35.10, 129.00], [35.12, 129.05], [35.14, 129.10], [35.16, 129.15], [35.18, 129.20]],
  // 빌드 47 폴드 접음에서 6번(해운대석각)이 창 윗변에 걸린 당일 여행(S15P21E201-1989) — 캡처에서 읽은 대략 좌표.
  '해운대당일': [[35.1640, 129.1595], [35.1655, 129.1585], [35.1615, 129.1700], [35.1590, 129.1720], [35.1610, 129.1640], [35.1540, 129.1525]],
};
// 🔴 실기기에서 걸린 것은 «처음 그릴 때»가 아니라 «창을 접어 맞춘 뒤 다시 열 때»였다(S15P21E201-1989). 폰 판은 그 전환도 본다:
//    접은 여백으로 그리고(고른 곳 1번, focus) → 창을 연 여백으로 __ensureKakaoMapFits 를 부른 뒤 열린 띠로 판정한다.
function collapsedOf(d) { return d.sheetOpen ? { ...d, bottom: d.collapsedBottom } : null; }
const colors = { navy: '#1F2A44', selected: '#D94141', canvas: '#FFFFFF', casing: '#FFFFFF' };

const html = buildKakaoMapHtml(key);
const browser = await chromium.launch();
const rows = [];
let fails = 0;
for (const d of devices) {
  const ctx = await browser.newContext({ viewport: { width: d.w, height: d.h }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  await page.route('https://j15e201.p.ssafy.io/__maptest', (r) => r.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html }));
  await page.goto('https://j15e201.p.ssafy.io/__maptest');
  await page.waitForFunction(() => window.kakao && window.kakao.maps && window.kakao.maps.LatLng, null, { timeout: 30000 });
  for (const [tripName, coords] of Object.entries(trips)) {
    const pad = fitPadding(d.bottom, d.h, d.top, d.w);
    const stops = coords.map(([latitude, longitude], i) => ({ id: `s${i + 1}`, number: i + 1, name: `장소 ${i + 1}`, latitude, longitude }));
    const coll = collapsedOf(d);
    const collPad = coll ? fitPadding(coll.bottom, d.h, d.top, d.w) : null;
    const res = await page.evaluate(({ stops, pad, colors, collPad }) => {
      if (collPad) {
        window.__renderKakaoMap({ stops, points: [], routes: [], selectedId: 's1', colors, focus: true, shiftY: 0, fitPadding: collPad });
        window.__ensureKakaoMapFits && window.__ensureKakaoMapFits({ fitPadding: pad, shiftY: 0 });
      } else window.__renderKakaoMap({ stops, points: [], routes: [], selectedId: null, colors, focus: false, shiftY: 0, fitPadding: pad });
      return new Promise((done) => setTimeout(() => {
        const m = window.kakao.maps; const proj = map.getProjection();
        done({ level: map.getLevel(), pts: stops.map((s) => { const p = proj.containerPointFromCoords(new m.LatLng(s.latitude, s.longitude)); return { n: s.number, x: p.x, y: p.y }; }) });
      }, 900));
    }, { stops, pad, colors, collPad });
    const bad = res.pts.filter((p) => p.y - R < d.top || p.y + R > d.h - d.bottom || p.x - R < 0 || p.x + R > d.w).map((p) => p.n);
    let minGap = Infinity;
    for (let i = 0; i < res.pts.length; i++) for (let j = i + 1; j < res.pts.length; j++) minGap = Math.min(minGap, Math.hypot(res.pts[i].x - res.pts[j].x, res.pts[i].y - res.pts[j].y));
    // 가림 띠를 그려 넣고 찍는다.
    await page.evaluate(({ top, bottom }) => {
      document.querySelectorAll('.__band').forEach((e) => e.remove());
      for (const [t, hgt] of [[0, top], [innerHeight - bottom, bottom]]) { const b = document.createElement('div'); b.className = '__band'; Object.assign(b.style, { position: 'fixed', left: 0, right: 0, top: t + 'px', height: hgt + 'px', background: 'rgba(255,0,0,.25)', zIndex: 9999, pointerEvents: 'none' }); document.body.appendChild(b); }
    }, d);
    const shot = path.join(outDir, `${d.name}-${tripName}.png`.replace(/[\\/:*?"<>|]/g, '_'));
    await page.screenshot({ path: shot });
    const band = d.h - d.top - d.bottom;
    const ok = bad.length === 0;
    if (!ok) fails++;
    rows.push({ device: d.name, trip: tripName, band, pad: pad.join('/'), level: res.level, minGap: Math.round(minGap), hidden: bad.join(',') || '-', ok });
  }
  await ctx.close();
}
await browser.close();
writeFileSync(path.join(outDir, 'result.json'), JSON.stringify(rows, null, 2));
for (const r of rows) console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.device.padEnd(16)} ${r.trip.padEnd(8)} 보이는띠=${r.band} 여백=${r.pad} level=${r.level} 점간최소=${r.minGap}px 가림=${r.hidden}`);
console.log(fails ? `실패 ${fails}` : '모두 통과');
process.exit(fails ? 1 : 0);
