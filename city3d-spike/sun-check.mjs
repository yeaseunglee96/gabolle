// 태양 계산이 맞게 다시 쓰였는지 채점한다.
//
// 채점표는 내가 정한 것이 아니다. 팀의 그림자 분석(bigData)이 같은 계산을 파이썬으로
// 해서 남긴 결과를 그대로 읽어 쓴다. 그 파이썬 소스는 지금 저장소에 없지만
// (`__pycache__` 만 남았고 git 도 모른다) **결과는 남아 있다.**
//
// 그래서 이 검사는 "내가 믿는 값과 맞나" 가 아니라
// "이미 검증된 다른 구현과 같은 답을 내나" 를 묻는다. 그게 훨씬 강한 질문이다.
//
//   node sun-check.mjs     종료 코드 0 이면 통과

import fs from 'node:fs';
import path from 'node:path';
import { sunPosition, solarNoonMinutes, shadowLengthM, kst } from './public/sun.mjs';

const ROOT = path.resolve(import.meta.dirname, '..');
const SUMMARY = path.join(ROOT, 'bigData/data/staged/_shadow-summary.json');

// 팀의 그림자 분석이 쓴 기준점. 남중 시각 12:30 KST 가 이 경도를 가리킨다.
const LAT = 35.1796;
const LON = 129.0756;
const DATE = [2026, 7, 15];

const rows = [];
const check = (name, got, want, tol, unit = '') => {
  const ok = Math.abs(got - want) <= tol;
  rows.push({ ok, name, got: +got.toFixed(2), want: `${want} ± ${tol}${unit}` });
};

if (!fs.existsSync(SUMMARY)) {
  console.error(`채점표가 없습니다: ${path.relative(ROOT, SUMMARY)}`);
  console.error('bigData 의 그림자 분석 결과가 있어야 이 검사를 돌릴 수 있습니다.');
  process.exit(1);
}
const summary = JSON.parse(fs.readFileSync(SUMMARY, 'utf8'));

// ── 1) 시각별 해의 위치 — 가장 강한 검사다 ──────────────────────────
// 다섯 시각의 고도와 방위가 모두 맞으면 계산 전체가 맞은 것이다.
for (const [hour, rec] of Object.entries(summary.byHour)) {
  const s = sunPosition(kst(...DATE, +hour), LAT, LON);
  check(`${hour}시 해의 고도`, s.altitude, rec.sunAltDeg, 0.5, '°');
  check(`${hour}시 해의 방위`, s.azimuth, rec.sunAzDeg, 0.5, '°');
}

// ── 2) 남중 ────────────────────────────────────────────────────────
const noonMin = solarNoonMinutes(kst(...DATE, 12), LON, 9);
check('남중 시각 (자정부터 분)', noonMin, 12 * 60 + 30, 5, '분');

// 남중 순간의 고도와 방위
const atNoon = sunPosition(kst(...DATE, 0, 0).getTime ? new Date(kst(...DATE, 0, 0).getTime() + noonMin * 60000) : null, LAT, LON);
check('남중고도', atNoon.altitude, 77, 2, '°');
check('남중 방위 (남쪽)', atNoon.azimuth, 180, 5, '°');

// ── 3) 그림자 길이 — 화면에 실제로 쓰는 식 ──────────────────────────
// 이게 틀리면 건물은 제자리인데 그림자만 엉뚱한 길이로 눕는다.
for (const c of summary.invariants.checks) {
  const m = c.name.match(/^60m 건물 · 해 (\d+)° → 그림자/);
  if (!m) continue;
  check(c.name, shadowLengthM(60, +m[1]), parseFloat(c.want), parseFloat(c.want.split('±')[1]), ' m');
}
// 시각별 그림자 길이도 기록과 맞춰 본다
for (const [hour, rec] of Object.entries(summary.byHour)) {
  check(`${hour}시 60m 건물의 그림자`, shadowLengthM(60, rec.sunAltDeg), rec.shadowOf60mBuildingM, 1, ' m');
}

// ── 4) 방향 상식 ───────────────────────────────────────────────────
const morning = sunPosition(kst(...DATE, 9), LAT, LON);
const evening = sunPosition(kst(...DATE, 17), LAT, LON);
rows.push({ ok: morning.azimuth < 180, name: '오전 9시 해는 동쪽', got: +morning.azimuth.toFixed(1), want: '< 180°' });
rows.push({ ok: evening.azimuth > 180, name: '오후 5시 해는 서쪽', got: +evening.azimuth.toFixed(1), want: '> 180°' });

// ── 결과 ───────────────────────────────────────────────────────────
const pad = (s, n) => String(s).padEnd(n);
console.log(`채점표: ${path.relative(ROOT, SUMMARY)}`);
console.log(`기준점: 위도 ${LAT} 경도 ${LON} · ${DATE.join('-')} · ${summary.timezone}\n`);
for (const r of rows) {
  console.log(`  ${r.ok ? 'OK ' : '틀림'}  ${pad(r.name, 26)} ${pad(r.got, 10)} ${r.want}`);
}
const bad = rows.filter((r) => !r.ok);
console.log(`\n${rows.length} 항목 중 ${rows.length - bad.length} 통과.`);
if (bad.length) {
  console.log('틀린 항목:');
  for (const r of bad) console.log(`  - ${r.name}: ${r.got} (원하는 값 ${r.want})`);
}
process.exit(bad.length ? 1 : 0);
