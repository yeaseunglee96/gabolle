// 앱에 넣을 브랜드 자산 — 로고(동백이 워드마크) · 안드로이드 적응형 아이콘 · 파비콘 · 스플래시
// 앱 아이콘 B(B3-bridge-photo-logo.png)와 도장 2b(stamp-busan-arrived-logo.png)는 이미 있다 — 복사만 한다.
const { chromium } = require('@playwright/test'); const fs = require('fs'); const path = require('path');
const FE = 'C:/Users/SSAFY/Desktop/특화 프로젝트/S15P21E201/frontend';
const OUT = path.join(__dirname, 'brand-out'); fs.mkdirSync(OUT, { recursive: true });
const b64 = (p) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const mascot = b64(path.join(__dirname, 'theme-bnk', 'preview', 'dongbaek-idle.png')); // 팀의 새 동백이(꽃) — 저장소의 옛 핀 그림이 아니다
const gwangalli = b64(path.join(__dirname, 'theme-bnk', 'preview', 'gwangalli.png'));
const INK = '#191919';
const FONT = `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Montserrat:wght@800&family=Noto+Sans+KR:wght@700&display=swap">`;
// 워드마크 — GAB[동백이]LLE. 동백이는 O 자리. 자간(size*0.08)이 B 뒤에 붙어 오른쪽으로 치우치므로 왼쪽 여백만 음수로 상쇄한다.
const mark = (size, color, m = size * 1.3, extra = '') => `<div style="display:flex;align-items:center;font-family:Montserrat,sans-serif;font-weight:800;font-size:${size}px;letter-spacing:${size * 0.08}px;color:${color};line-height:1;${extra}"><span>GAB</span><img src="${mascot}" style="width:${m}px;height:${m}px;object-fit:contain;margin:0 ${size * 0.02}px 0 ${-size * 0.06}px;${extra.includes('white-silhouette') ? 'filter:brightness(0) invert(1);' : ''}"><span>LLE</span></div>`;
const PAGES = {
  // 로고 — 2400×800 투명. 현행 gabolle-logo-hd.png(2170×725, 3:1)와 같은 비율대라 화면의 width/height 값을 안 바꿔도 된다.
  'gabolle-logo-hd': { w: 2400, h: 800, bg: 'transparent', html: `<div style="width:2400px;height:800px;display:flex;align-items:center;justify-content:center;">${mark(400, INK, 500)}</div>` },
  'gabolle-logo-night': { w: 2400, h: 800, bg: 'transparent', html: `<div style="width:2400px;height:800px;display:flex;align-items:center;justify-content:center;">${mark(400, '#FFFFFF', 500)}</div>` },
  // 안드로이드 적응형 아이콘 — 바탕은 광안대교 사진(글자 없음), 앞면은 워드마크 + 부산 가볼래? 를 안전 영역(가운데 66% 원) 안에 넣는다.
  'android-icon-background': { w: 1024, h: 1024, bg: '#0B1424', html: `<div style="width:1024px;height:1024px;position:relative;overflow:hidden;background:#0B1424;"><img src="${gwangalli}" style="position:absolute;left:-1010px;top:-975px;width:2300px;height:2960px;object-fit:cover;filter:brightness(1.35) contrast(1.15) saturate(1.15);"><div style="position:absolute;inset:0;background:linear-gradient(180deg, rgba(11,20,36,0.45) 0%, rgba(11,20,36,0) 40%, rgba(11,20,36,0) 70%, rgba(11,20,36,0.5) 100%);"></div></div>` },
  'android-icon-foreground': { w: 1024, h: 1024, bg: 'transparent', html: `<div style="width:1024px;height:1024px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;">${mark(84, '#fff', 108, 'text-shadow:0 6px 24px rgba(0,0,0,0.45);')}<span style="font-family:'Noto Sans KR',sans-serif;font-weight:700;font-size:38px;color:#fff;letter-spacing:2px;text-shadow:0 4px 18px rgba(0,0,0,0.5);">부산 가볼래?</span></div>` },
  'android-icon-monochrome': { w: 1024, h: 1024, bg: 'transparent', html: `<div style="width:1024px;height:1024px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;">${mark(84, '#fff', 108, 'white-silhouette')}<span style="font-family:'Noto Sans KR',sans-serif;font-weight:700;font-size:38px;color:#fff;letter-spacing:2px;">부산 가볼래?</span></div>` },
  // 스플래시 — 흰 바탕에 동백이 + 워드마크 (expo 템플릿의 과녁 그림을 대신한다)
  'splash-icon': { w: 1024, h: 1024, bg: '#FFFFFF', html: `<div style="width:1024px;height:1024px;background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;"><img src="${mascot}" style="width:520px;height:520px;object-fit:contain;">${mark(96, INK, 122)}</div>` },
  // 파비콘 — 동백이만
  'favicon': { w: 192, h: 192, bg: 'transparent', html: `<div style="width:192px;height:192px;display:flex;align-items:center;justify-content:center;"><img src="${mascot}" style="width:184px;height:184px;object-fit:contain;"></div>` },
};
(async () => {
  const b = await chromium.launch();
  for (const [n, p] of Object.entries(PAGES)) {
    const pg = await b.newPage({ viewport: { width: p.w, height: p.h } });
    await pg.setContent(`${FONT}<body style="margin:0;background:${p.bg}">${p.html}</body>`); await pg.waitForTimeout(1500);
    await pg.screenshot({ path: path.join(OUT, n + '.png'), omitBackground: p.bg === 'transparent', clip: { x: 0, y: 0, width: p.w, height: p.h } });
    await pg.close();
  }
  // 파비콘은 48 로 줄인다 (현행 크기)
  const pg = await b.newPage({ viewport: { width: 48, height: 48 } });
  await pg.setContent(`<body style="margin:0;background:transparent"><img src="${b64(path.join(OUT, 'favicon.png'))}" style="width:48px;height:48px;display:block"></body>`);
  await pg.screenshot({ path: path.join(OUT, 'favicon.png'), omitBackground: true, clip: { x: 0, y: 0, width: 48, height: 48 } });
  // 시트 — 검토용
  const im = (n, s, bg) => `<div style="background:${bg};padding:12px;display:inline-block"><img src="${b64(path.join(OUT, n + '.png'))}" style="width:${s}px;display:block"></div>`;
  const p2 = await b.newPage({ viewport: { width: 1200, height: 900 } });
  await p2.setContent(`<body style="margin:0;background:#888;display:flex;flex-wrap:wrap;gap:12px;padding:12px">${im('gabolle-logo-hd', 420, '#fff')}${im('gabolle-logo-night', 420, '#0B2A5C')}${im('android-icon-foreground', 240, '#4a6')}${im('android-icon-background', 240, '#fff')}${im('android-icon-monochrome', 240, '#333')}${im('splash-icon', 240, '#fff')}${im('favicon', 48, '#fff')}</body>`);
  await p2.waitForTimeout(300); await p2.screenshot({ path: path.join(OUT, '_sheet.png'), fullPage: true });
  await b.close(); console.log('brand assets ok');
})();
