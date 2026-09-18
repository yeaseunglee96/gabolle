// 메뉴판 화면 문구가 다섯 언어로 다 있는가 — S15P21E201-1295.
//
// 🔴 왜 검사로 두는가
//
// `tx(ko, en)` 는 카탈로그(src/i18n/translations.ts)에 그 한국어 원문이 있으면 그 언어로
// 바꾸고, **없으면 영어로 떨어진다.** 조용히 떨어진다 — 오류도 경고도 없다. 그래서 문구를
// 새로 넣은 사람 눈에는 아무 문제가 없고, **한국어로 화면을 보면 끝까지 멀쩡하다.**
//
// 실제로 그렇게 나갔다. 사용자가 번체 중국어로 시험한 화면에 「Hide」·「What is this
// dish?」·「Added by AI — not read from the photo」가 영어로 떠 있었다(2026-09-19).
// 화면의 나머지는 전부 번체였다. 그날 올린 문구 여덟 개가 전부 카탈로그에 없었다.
//
// 🔴 특히 나쁜 자리가 있다. 「AI 가 그린 그림이에요」와 「AI 가 덧붙인 설명이에요」는
// 이 기능이 **거짓말을 안 하는 유일한 장치**다. 그 두 줄이 영어로 떨어지면, 영어를 못
// 읽는 사람에게는 **아무 말도 안 한 것과 같다** — 만든 그림을 식당 사진으로 본다.
//
// 사용법: node tools/check-menu-translations.mjs
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');

/** 이 검사가 지키는 화면들. 늘려도 된다 — 줄이지 않는다. */
const SCREENS = ['app/field/menu-scan.tsx'];

/** 🔴 출처를 밝히는 문구. 이것이 빠지면 기능이 사용자를 속이는 것이 된다. */
const MUST_BE_TRANSLATED = [
  'AI 가 그린 그림이에요 — 실제 나오는 음식과 달라요',
  '사진에서 읽은 것이 아니라 AI 가 덧붙인 설명이에요',
];

/** `tx('...', '...')` 의 첫 인자 중 값이 끼지 않는 것만 모은다. */
function fixedKoreanStrings(source) {
  const found = new Set();
  for (const match of source.matchAll(/tx\(\s*(['`])([\s\S]*?)\1\s*,/g)) {
    const korean = match[2];
    // 값이 끼는 문구(`${name} 을(를) …`)는 원문이 매번 달라 카탈로그 키가 될 수 없다.
    if (korean.includes('${')) continue;
    found.add(korean);
  }
  return [...found];
}

const catalog = readFileSync(join(ROOT, 'src/i18n/translations.ts'), 'utf8');

/** 카탈로그에 그 키가 있고 세 언어가 다 채워져 있나. */
function entryOf(key) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const line = new RegExp("^\\s*'" + escaped + "':\\s*\\{(.*)\\},?\\s*$", 'm').exec(catalog);
  if (!line) return null;
  const body = line[1];
  return {
    ja: /\bja:\s*'[^']/.test(body),
    zhHans: /\bzhHans:\s*'[^']/.test(body),
    zhHant: /\bzhHant:\s*'[^']/.test(body),
  };
}

const missing = [];
const incomplete = [];
let checked = 0;

for (const screen of SCREENS) {
  for (const key of fixedKoreanStrings(readFileSync(join(ROOT, screen), 'utf8'))) {
    checked += 1;
    const entry = entryOf(key);
    if (entry === null) { missing.push(`${screen}  ${key}`); continue; }
    const holes = ['ja', 'zhHans', 'zhHant'].filter((field) => !entry[field]);
    if (holes.length > 0) incomplete.push(`${screen}  ${key}  (빠진 언어: ${holes.join(' · ')})`);
  }
}

for (const key of MUST_BE_TRANSLATED) {
  const entry = entryOf(key);
  if (entry === null || !entry.ja || !entry.zhHans || !entry.zhHant) {
    missing.push(`🔴 출처를 밝히는 문구  ${key}`);
  }
}

// 정규식이 헛돌면 이 검사가 조용히 통과한다. 그것도 실패로 친다.
if (checked < 10) {
  console.error(`🔴 화면에서 찾은 문구가 ${checked}개뿐입니다 — 정규식이 안 맞는 것 같습니다.`);
  console.error('   tx(ko, en) 을 부르는 모양이 바뀌었는지 보세요.');
  process.exit(1);
}

if (missing.length > 0 || incomplete.length > 0) {
  console.error('🔴 메뉴판 화면 문구가 카탈로그에 없습니다 (S15P21E201-1295).\n');
  for (const line of [...missing, ...incomplete]) console.error('  ' + line);
  console.error('\nsrc/i18n/translations.ts 에 그 한국어 원문을 키로 ja·zhHans·zhHant 를 채우세요.');
  console.error('안 채우면 그 문구만 영어로 떨어집니다 — 한국어로 보면 안 보이는 결함입니다.');
  process.exit(1);
}

console.log(`메뉴판 문구 번역 검사 통과 (고정 문구 ${checked}개, 세 언어 모두)`);
