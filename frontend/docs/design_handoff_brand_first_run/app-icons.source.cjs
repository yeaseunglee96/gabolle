// 앱 아이콘 3차 4안 — A 는 톤 바탕으로 단단하게, B 는 광안대교를 키우고 그 위에 로고, C 는 밤 광안대교 벡터, D 는 낮 선화
const { chromium } = require('@playwright/test'); const fs = require('fs'); const path = require('path');
const b64 = (p) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const mascot = b64(path.join(__dirname, '..', 'theme-bnk', 'preview', 'dongbaek-idle.png'));
const gwangalli = b64(path.join(__dirname, '..', 'theme-bnk', 'preview', 'gwangalli.png'));
const RED = '#D83A48', INK = '#191919', TINT = '#FCE8EA';
const FONT = `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Montserrat:wght@800;900&family=Noto+Sans+KR:wght@700&display=swap">`;
const mark = (size, color, m = size * 1.3, shadow = false) => `<div style="display:flex;align-items:center;gap:${size * 0.02}px;font-family:Montserrat,sans-serif;font-weight:800;font-size:${size}px;letter-spacing:${size * 0.08}px;color:${color};line-height:1;${shadow ? 'text-shadow:0 6px 24px rgba(0,0,0,0.45);' : ''}"><span>GAB</span><img src="${mascot}" style="width:${m}px;height:${m}px;object-fit:contain;margin:0 ${size * 0.08 - size * 0.06}px 0 ${-size * 0.06}px;${shadow ? 'filter:drop-shadow(0 6px 18px rgba(0,0,0,0.45));' : ''}"><span>LLE</span></div>`;
// 광안대교 — 현수교 두 주탑 · 두 곡선 케이블 · 행어 · 상판. 선 색과 굵기를 받는다
const bridge = (w, color, sw = 7, lights = false) => {
  const hang = [];
  for (let x = 30; x <= 370; x += 17) { const t = x < 200 ? (x - 30) / 150 : (370 - x) / 150; const y = 96 - (24 + 44 * Math.pow(Math.abs(x - 200) / 170, 2)) * 1; hang.push(`M${x} 96 V${Math.max(40, 96 - (10 + 46 * Math.pow(Math.abs(x - 200) / 170, 2)))}`); }
  const dots = lights ? Array.from({ length: 26 }, (_, i) => { const x = 30 + i * 13.6; const y = 96 - (10 + 46 * Math.pow(Math.abs(x - 200) / 170, 2)); return `<circle cx="${x}" cy="${y}" r="2.2" fill="#FFD27A"></circle>`; }).join('') : '';
  return `<svg viewBox="0 0 400 160" width="${w}" height="${w * 0.4}" aria-hidden="true" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"><path d="M6 96 H394"></path><path d="M96 96 V24 M304 96 V24" stroke-width="${sw * 1.5}"></path><path d="M6 60 Q96 20 200 84 Q304 20 394 60"></path><path d="${hang.join(' ')}" stroke-width="${sw * 0.45}"></path>${dots}<path d="M20 126 q22-14 44 0 t44 0 t44 0 t44 0 t44 0 t44 0 t44 0 t44 0" stroke-width="${sw}"></path></svg>`;
};
const ICONS = {
  'A3-tint-mascot-name': `<div style="width:1024px;height:1024px;background:#FFFFFF;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;"><img src="${mascot}" style="width:640px;height:640px;object-fit:contain;filter:drop-shadow(0 24px 40px rgba(216,58,72,0.28));">${mark(112, INK, 142)}</div>`,
  'B3-bridge-photo-logo': `<div style="width:1024px;height:1024px;position:relative;overflow:hidden;background:#0B1424;"><img src="${gwangalli}" style="position:absolute;left:-1010px;top:-975px;width:2300px;height:2960px;object-fit:cover;filter:brightness(1.35) contrast(1.15) saturate(1.15);"><div style="position:absolute;inset:0;background:linear-gradient(180deg, rgba(11,20,36,0.45) 0%, rgba(11,20,36,0) 40%, rgba(11,20,36,0) 70%, rgba(11,20,36,0.5) 100%);"></div><div style="position:absolute;left:0;right:0;top:238px;display:flex;flex-direction:column;align-items:center;gap:24px;">${mark(126, '#fff', 160, true)}<span style="font-family:'Noto Sans KR',sans-serif;font-weight:700;font-size:56px;color:#fff;letter-spacing:2px;text-shadow:0 4px 18px rgba(0,0,0,0.5);">부산 가볼래?</span></div></div>`,
  'C3-night-bridge-vector': `<div style="width:1024px;height:1024px;position:relative;overflow:hidden;background:linear-gradient(180deg, #0F1B33 0%, #1B2E55 58%, #0E1830 100%);display:flex;flex-direction:column;align-items:center;justify-content:center;"><svg style="position:absolute;inset:0;" viewBox="0 0 1024 1024" width="1024" height="1024" aria-hidden="true">${Array.from({ length: 40 }, (_, i) => `<circle cx="${(i * 197) % 1024}" cy="${(i * 131) % 420}" r="${1.5 + (i % 3)}" fill="#fff" opacity="${0.35 + (i % 4) * 0.15}"></circle>`).join('')}<circle cx="820" cy="180" r="54" fill="#FFF0C2" opacity="0.95"></circle><rect x="0" y="720" width="1024" height="304" fill="#0B1428" opacity="0.7"></rect>${Array.from({ length: 12 }, (_, i) => `<rect x="${60 + i * 80}" y="${740 + (i % 3) * 40}" width="${30 + (i % 4) * 22}" height="6" rx="3" fill="#FFD27A" opacity="${0.25 + (i % 3) * 0.15}"></rect>`).join('')}</svg><div style="position:relative;display:flex;flex-direction:column;align-items:center;gap:36px;margin-top:-40px;">${mark(126, '#fff', 160, true)}${bridge(880, '#F5F5F7', 8, true)}</div></div>`,
  'D3-day-bridge-lineart': `<div style="width:1024px;height:1024px;background:#FFFFFF;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:34px;">${mark(140, INK, 176)}<span style="font-family:Montserrat,sans-serif;font-weight:800;font-size:42px;letter-spacing:14px;color:${RED};">BUSAN</span>${bridge(800, INK, 8)}</div>`,
};
(async () => {
  const b = await chromium.launch(); const pg = await b.newPage({ viewport: { width: 1024, height: 1024 } });
  for (const [n, html] of Object.entries(ICONS)) {
    await pg.setContent(`${FONT}<body style="margin:0">${html}</body>`); await pg.waitForTimeout(1500);
    await pg.screenshot({ path: path.join(__dirname, n + '.png'), clip: { x: 0, y: 0, width: 1024, height: 1024 } });
  }
  const names = Object.keys(ICONS); const labels = { 'A3-tint-mascot-name': 'A · 동백이 + GABOLLE · 흰 바탕', 'B3-bridge-photo-logo': 'B · 광안대교 사진 위 로고', 'C3-night-bridge-vector': 'C · 밤 광안대교 · 벡터', 'D3-day-bridge-lineart': 'D · 낮 광안대교 선화' };
  const cell = (n, s) => `<img src="data:image/png;base64,${fs.readFileSync(path.join(__dirname, n + '.png')).toString('base64')}" style="width:${s}px;height:${s}px;border-radius:${Math.round(s * 0.225)}px;display:block;box-shadow:0 ${s / 30}px ${s / 10}px rgba(0,0,0,0.18);">`;
  const rowOf = (bg, fg) => `<div style="display:flex;gap:56px;padding:40px 56px;background:${bg};align-items:flex-end;">${names.map((n) => `<div style="display:flex;flex-direction:column;align-items:center;gap:16px;width:220px;">${cell(n, 180)}<div style="display:flex;gap:14px;align-items:center;">${cell(n, 60)}${cell(n, 40)}</div><span style="font:700 15px 'Noto Sans KR',sans-serif;color:${fg};">${labels[n]}</span></div>`).join('')}</div>`;
  const p2 = await b.newPage({ viewport: { width: 1280, height: 800 } }); await p2.setContent(`<body style="margin:0;background:#888;"><div style="width:1280px;">${rowOf('#F5F5F7', '#191919')}${rowOf('#161619', '#DADADF')}</div></body>`); await p2.waitForTimeout(200);
  await p2.screenshot({ path: path.join(__dirname, 'sheet3.png'), fullPage: true });
  await b.close(); console.log('icons3 ok');
})();
