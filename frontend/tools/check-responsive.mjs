// 화면 44개 × 폭 7개 = 300번 넘는 확인을 손으로 할 수 없어서 자동화한다 (S15P21E201-283).
// 사람은 여기가 골라준 목록과 구조상 어려운 화면에만 집중하면 된다.
//
// app/ 밑의 라우트 파일을 스스로 찾아서 돈다 — 몇 개인지 여기 적지 않는다.
// 화면이 늘면 이 숫자가 낡고, 낡은 기준은 없는 기준보다 나쁘다(CONTRIBUTING.md 4절).
// 대신 새 라우트 파일을 추가하면 다음 실행에서 저절로 검사 대상에 들어간다.
//
// 사용법: node tools/check-responsive.mjs <base URL>
import { chromium } from 'playwright';
import { mkdirSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const APP_DIR = join(ROOT, 'app');
const REPORT_DIR = join(ROOT, 'tools', 'responsive-report');

// 상세설계서 v2·기획서 v7 12.1 이 못박은 일곱 폭 — S15P21E201-144.
const WIDTHS = [360, 375, 414, 768, 1024, 1440, 1920];
const HEIGHT = 900;

// 이 파일들은 라우트가 아니라 뼈대다 — 방문 대상에서 뺀다.
const SKIP_BASENAMES = new Set(['_layout.tsx', '_layout.ts', '+not-found.tsx']);

function listRouteFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...listRouteFiles(full));
    } else if ((entry.name.endsWith('.tsx') || entry.name.endsWith('.ts')) && !SKIP_BASENAMES.has(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

// expo-router 파일 경로 -> 실제 주소.
//   (그룹) 세그먼트는 주소에 안 나타난다
//   index 는 부모 경로 자체를 가리킨다
//   [id] 같은 동적 세그먼트는 임의값 하나로 채운다 — 구조(레이아웃)를 보는
//   검사라 실존하는 값일 필요는 없다. 없는 자원이면 그 화면의 "찾을 수 없음"
//   상태를 검사하는 셈이고, 그 상태에서 가로 스크롤이 나도 잡아야 할 결함이다.
function fileToRoute(file) {
  const rel = relative(APP_DIR, file).replace(/\\/g, '/').replace(/\.tsx?$/, '');
  const segments = rel.split('/').filter((segment) => !/^\(.*\)$/.test(segment)).map((segment) => (segment.startsWith('[') ? 'demo' : segment));
  const withoutIndex = segments.filter((segment, index) => !(segment === 'index' && index === segments.length - 1));
  return '/' + withoutIndex.join('/');
}

function sanitize(route) {
  return route.replace(/^\//, '').replace(/\//g, '_') || 'root';
}

const baseUrl = process.argv[2];
if (!baseUrl) {
  console.error('사용법: node tools/check-responsive.mjs <base URL>');
  process.exit(1);
}

const routes = [...new Set(listRouteFiles(APP_DIR).map(fileToRoute))].sort();

mkdirSync(REPORT_DIR, { recursive: true });

const browser = await chromium.launch();
let page = await browser.newPage();

const violations = [];
const errors = [];

for (const route of routes) {
  for (const width of WIDTHS) {
    try {
      await page.setViewportSize({ width, height: HEIGHT });
      await page.goto(`${baseUrl}${route}`, { waitUntil: 'networkidle', timeout: 30000 });
      // Expo Router 웹은 클라이언트 사이드 렌더링이라 마지막 프레임이 조금 늦게 온다.
      await page.waitForTimeout(500);

      // clientWidth 는 스크롤바를 뺀 값이라, scrollWidth 가 그보다 크면 실제로
      // 가로로 넘친 것이다 — 스크롤바 자체를 오탐으로 잡지 않는다.
      const overflow = await page.evaluate(() => {
        const doc = document.documentElement;
        return { scrollWidth: doc.scrollWidth, clientWidth: doc.clientWidth };
      });

      if (overflow.scrollWidth > overflow.clientWidth) {
        const shotName = `${sanitize(route)}-${width}.png`;
        await page.screenshot({ path: join(REPORT_DIR, shotName), fullPage: true });
        violations.push({ route, width, overflowPx: overflow.scrollWidth - overflow.clientWidth, screenshot: shotName });
      }
    } catch (err) {
      // 🔴 여기서 못 잡으면 페이지 크래시 하나가 남은 검사 전부를 끌고 죽는다
      // (실측 — /place/demo 하나가 죽자 나머지 300번 넘는 검사가 통째로 안 돌았다).
      // 검사 못 한 것을 통과로 치지 않는다 — 아래서 종료 코드를 1로 낸다.
      console.error(`🔴 검사할 수 없다: ${route} (${width}px) — ${err.message}`);
      errors.push({ route, width, message: err.message });
      // 크래시한 페이지는 이후 호출도 전부 죽는다. 새로 띄워서 다음 검사를 이어간다.
      if (page.isClosed() || err.message.includes('crashed')) {
        await page.close().catch(() => {});
        page = await browser.newPage();
      }
    }
  }
}

await browser.close();

console.log(`검사한 화면 ${routes.length}개 × 폭 ${WIDTHS.length}개 = ${routes.length * WIDTHS.length}번`);

if (errors.length > 0) {
  console.error(`\n🔴 열지 못했거나 크래시한 검사 ${errors.length}건 — 못 본 것은 통과로 치지 않는다`);
  for (const e of errors) console.error(`  ${e.route}  ${e.width}px  ${e.message}`);
}

if (violations.length > 0) {
  console.error(`\n🔴 가로 스크롤 ${violations.length}건 — tools/responsive-report/ 에 캡처를 남겼다`);
  for (const v of violations) {
    console.error(`  ${v.route}  ${v.width}px  (+${v.overflowPx}px)  → ${v.screenshot}`);
  }
}

if (violations.length > 0 || errors.length > 0) {
  process.exit(1);
}

console.log('가로 스크롤 없음 — 통과.');
