// 여행표 도장 — 부산 느낌 3안 (광안대교 · 파도 · 날짜). 한 색(동백), 가볍게 거친 잉크
const { chromium } = require('@playwright/test'); const fs = require('fs'); const path = require('path');
const RED = '#D83A48';
const inkMascot = 'data:image/png;base64,' + fs.readFileSync(path.join(__dirname, 'stamp-dongbaek.png')).toString('base64'); // 잉크 처리한 진짜 동백이
const FONT = `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Montserrat:wght@700;800;900&display=swap">`;
const rough = `<filter id="rough" x="-5%" y="-5%" width="110%" height="110%"><feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="2" seed="3"></feTurbulence><feDisplacementMap in="SourceGraphic" scale="5"></feDisplacementMap></filter>`;
// 광안대교 (0..400 × 0..120 좌표계) — 주탑 둘 · 케이블 · 행어 · 상판 · 파도
const bridgePaths = (sw) => { const hang = []; for (let x = 30; x <= 370; x += 17) hang.push(`M${x} 84 V${Math.max(34, 84 - (8 + 44 * Math.pow(Math.abs(x - 200) / 170, 2)))}`); return `<path d="M6 84 H394" stroke-width="${sw}"></path><path d="M96 84 V18 M304 84 V18" stroke-width="${sw * 1.5}"></path><path d="M6 50 Q96 14 200 72 Q304 14 394 50" stroke-width="${sw}"></path><path d="${hang.join(' ')}" stroke-width="${sw * 0.45}"></path>`; };
// 광안대교 정밀 — H형 주탑 둘(가로보 2단) · 이중 상판 · 주케이블 · 행어 (0..400 × 0..130)
const gwangan = (sw) => { const hang = []; for (let x = 24; x <= 376; x += 11) { if (Math.abs(x - 96) < 6 || Math.abs(x - 304) < 6) continue; const y = 92 - (6 + 46 * Math.pow(Math.abs(x - 200) / 176, 2)); hang.push(`M${x} 92 V${Math.max(32, y)}`); }
  return `<path d="M4 92 H396 M4 104 H396" stroke-width="${sw}"></path><path d="M14 92 V104 M40 92 V104 M66 92 V104 M126 92 V104 M152 92 V104 M178 92 V104 M222 92 V104 M248 92 V104 M274 92 V104 M334 92 V104 M360 92 V104 M386 92 V104" stroke-width="${sw * 0.5}"></path><path d="M88 110 V14 M104 110 V14 M296 110 V14 M312 110 V14" stroke-width="${sw * 1.2}"></path><path d="M88 30 H104 M88 58 H104 M296 30 H312 M296 58 H312" stroke-width="${sw}"></path><path d="M4 66 Q96 8 200 76 Q304 8 396 66" stroke-width="${sw * 1.1}"></path><path d="${hang.join(' ')}" stroke-width="${sw * 0.4}"></path>`; };
const waves = (y, sw) => `<path d="M20 ${y} q22-13 44 0 t44 0 t44 0 t44 0 t44 0 t44 0 t44 0 t44 0" stroke-width="${sw}"></path>`;
const gull = (x, y, s) => `<path d="M${x - s} ${y} q${s / 2} -${s * 0.7} ${s} 0 q${s / 2} -${s * 0.7} ${s} 0" stroke-width="${s * 0.22}"></path>`;
const G = `fill="none" stroke="${RED}" stroke-linecap="round" stroke-linejoin="round"`;
const arc = (id, r, top) => top ? `<path id="${id}" d="M ${512 - r} 512 a ${r} ${r} 0 1 1 ${2 * r} 0"></path>` : `<path id="${id}" d="M ${512 - r} 512 a ${r} ${r} 0 0 0 ${2 * r} 0"></path>`;
const arcText = (id, text, size, ls = 10, weight = 800) => `<text font-family="Montserrat, Arial, sans-serif" font-weight="${weight}" font-size="${size}" fill="${RED}" letter-spacing="${ls}"><textPath href="#${id}" startOffset="50%" text-anchor="middle">${text}</textPath></text>`;

// 1 · 둥근 도장 — 겹 원 · 위 BUSAN · KOREA · 아래 GABOLLE · 가운데 광안대교와 파도 · 날짜
const S1 = `<svg viewBox="0 0 1024 1024" width="1024" height="1024" xmlns="http://www.w3.org/2000/svg"><defs>${rough}${arc('t1', 372, true)}${arc('b1', 372, false)}</defs>
<g filter="url(#rough)" ${G}><circle cx="512" cy="512" r="478" stroke-width="22"></circle><circle cx="512" cy="512" r="440" stroke-width="7"></circle><circle cx="512" cy="512" r="300" stroke-width="6" stroke-dasharray="3 14"></circle>
<g transform="translate(232 376) scale(1.4)">${bridgePaths(7)}${waves(104, 7)}${waves(122, 7)}</g>${gull(390, 326, 26)}${gull(445, 296, 20)}</g>
${arcText('t1', 'BUSAN · KOREA', 84, 14)}${arcText('b1', 'GABOLLE', 70, 16)}
<text x="512" y="620" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="46" fill="${RED}" letter-spacing="8">12 SEP 2026</text>
<circle cx="230" cy="512" r="9" fill="${RED}"></circle><circle cx="794" cy="512" r="9" fill="${RED}"></circle></svg>`;

// 2 · 입국 도장 — 둥근 네모 · ARRIVED · BUSAN 크게 · 날짜 · 아래 작은 다리
const S2 = `<svg viewBox="0 0 1024 1024" width="1024" height="1024" xmlns="http://www.w3.org/2000/svg"><defs>${rough}</defs>
<g filter="url(#rough)" ${G}><rect x="92" y="212" width="840" height="600" rx="54" stroke-width="20"></rect><rect x="126" y="246" width="772" height="532" rx="34" stroke-width="6"></rect><path d="M170 476 H854" stroke-width="6"></path><path d="M170 640 H854" stroke-width="6"></path>
<g transform="translate(262 646) scale(1.25)">${gwangan(5)}</g></g>
<text x="512" y="332" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="44" fill="${RED}" letter-spacing="18">ARRIVED · GABOLLE</text>
<text x="512" y="452" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="900" font-size="128" fill="${RED}" letter-spacing="10">BUSAN</text>
<text x="512" y="580" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="64" fill="${RED}" letter-spacing="12">12 · SEP · 2026</text></svg>`;

// 2b · 입국 도장 · 로고판 — 위 글자 없이 BUSAN · 날짜 · 아래 GAB✿LLE
const flower = (cx, cy, r) => [0, 72, 144, 216, 288].map((a) => `<ellipse cx="0" cy="${-r * 0.42}" rx="${r * 0.36}" ry="${r * 0.46}" fill="${RED}" transform="translate(${cx} ${cy}) rotate(${a})"></ellipse>`).join('') + `<circle cx="${cx}" cy="${cy}" r="${r * 0.2}" fill="#fff"></circle>`;
const S2b = `<svg viewBox="0 0 1024 1024" width="1024" height="1024" xmlns="http://www.w3.org/2000/svg"><defs>${rough}</defs>
<g filter="url(#rough)" ${G}><rect x="92" y="232" width="840" height="560" rx="54" stroke-width="20"></rect><rect x="126" y="266" width="772" height="492" rx="34" stroke-width="6"></rect><path d="M170 486 H854" stroke-width="6"></path><path d="M170 620 H854" stroke-width="6"></path></g>
<text x="512" y="450" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="900" font-size="148" fill="${RED}" letter-spacing="12">BUSAN</text>
<text x="512" y="576" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="62" fill="${RED}" letter-spacing="12">12 · SEP · 2026</text>
<text x="454" y="712" text-anchor="end" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="76" fill="${RED}" letter-spacing="4">GAB</text><image href="${inkMascot}" x="456" y="626" width="112" height="112"></image><text x="570" y="712" text-anchor="start" font-family="Montserrat, Arial, sans-serif" font-weight="800" font-size="76" fill="${RED}" letter-spacing="4">LLE</text></svg>`;

// 3 · 둥근 도장 · 단순 — 가운데 BUSAN 가로 · 위 다리 · 아래 파도 · 바깥 점선 링 · 작은 GABOLLE
const S3 = `<svg viewBox="0 0 1024 1024" width="1024" height="1024" xmlns="http://www.w3.org/2000/svg"><defs>${rough}${arc('b3', 400, false)}</defs>
<g filter="url(#rough)" ${G}><circle cx="512" cy="512" r="476" stroke-width="24"></circle><circle cx="512" cy="512" r="436" stroke-width="6" stroke-dasharray="4 16"></circle>
<g transform="translate(232 232) scale(1.4)">${bridgePaths(7)}</g><g transform="translate(232 560) scale(1.4)">${waves(60, 7)}${waves(80, 7)}</g>${gull(720, 300, 24)}${gull(770, 270, 18)}</g>
<text x="512" y="560" text-anchor="middle" font-family="Montserrat, Arial, sans-serif" font-weight="900" font-size="150" fill="${RED}" letter-spacing="12">BUSAN</text>
${arcText('b3', 'GABOLLE · TRIP PASS · 2026', 46, 10, 800)}</svg>`;
(async () => {
  const b = await chromium.launch(); const pg = await b.newPage({ viewport: { width: 1024, height: 1024 } });
  for (const [n, svg] of [['stamp-busan-round', S1], ['stamp-busan-arrived', S2], ['stamp-busan-arrived-logo', S2b], ['stamp-busan-simple', S3]]) {
    await pg.setContent(`${FONT}<body style="margin:0;background:transparent">${svg}</body>`); await pg.waitForTimeout(1300);
    await pg.screenshot({ path: path.join(__dirname, n + '.png'), omitBackground: true, clip: { x: 0, y: 0, width: 1024, height: 1024 } });
  }
  const im = (n) => `<img src="data:image/png;base64,${fs.readFileSync(path.join(__dirname, n + '.png')).toString('base64')}" style="width:300px;height:300px">`;
  const p2 = await b.newPage({ viewport: { width: 1020, height: 360 } }); await p2.setContent(`<body style="margin:0;background:#fff;display:flex;gap:30px;padding:30px">${im('stamp-busan-arrived')}${im('stamp-busan-arrived-logo')}${im('stamp-busan-simple')}</body>`); await p2.screenshot({ path: path.join(__dirname, '_stamps3.png') });
  await b.close(); console.log('stamp4 ok');
})();
