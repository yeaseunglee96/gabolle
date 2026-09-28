// 앱 전체 문구가 다섯 언어로 다 있는가 — S15P21E201-1340.
//
// 🔴 왜 검사로 두는가
//
// `tx(ko, en)` 는 카탈로그(src/i18n/translations.ts)에 그 한국어 원문이 있으면 그 언어로
// 바꾸고, **없으면 영어로 떨어진다.** 조용히 떨어진다 — 오류도 경고도 없다. 그래서 문구를
// 새로 넣은 사람 눈에는 아무 문제가 없고, **한국어로 화면을 보면 끝까지 멀쩡하다.**
//
// 메뉴판 화면에는 이미 check-menu-translations 가 있었다. 그 화면 밖에서 같은 구멍이
// 반복됐다 — 2026-09-19 실기(일본어)에서 여행 만들기 문항이 통째로 영어였고, 버스 카드
// 한 장 안에 네 언어가 섞여 있었다. 그래서 검사를 **앱 전체**로 넓힌다.
//
// 🔴 값이 끼는 문구는 카탈로그에 «올릴 수조차 없다» — 그것이 원래의 208곳
//
//   tx(`${trip.dayCount}일 · ${trip.partySize}명`, …)
//
// 열쇠가 실행할 때마다 달라지므로 어떤 줄을 넣어도 안 맞는다. 이 앱은 그것을 두 길로 푼다.
//
//   숫자만 낀다  →  그대로 둬도 된다. pickLanguage 가 숫자를 빼고 «모양»(「%d일 · %d명」)으로
//                  한 번 더 찾는다(S15P21E201-1344). 그 모양이 카탈로그에 있어야 한다 — 이 검사가 본다.
//   글자가 낀다  →  txf(tx, '%s의 기록', "%s's record", name) 로 쓴다(S15P21E201-1352).
//                  열쇠가 '%s의 기록' 으로 고정되어 카탈로그에 올라간다.
//
// 🔴 이 검사는 «값이 숫자인지» 는 모른다 — 소스만 보고는 알 수 없다. 그래서 값이 끼는
//    tx 는 모양만 확인한다. 이름·장소·날짜가 끼는 자리를 tx 로 두면 이 검사는 통과하지만
//    화면은 영어로 떨어진다. 그런 자리는 txf 로 쓴다.
//
// 사용법: node tools/check-translations.mjs
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');
const SCAN_DIRS = ['app', 'src'];
const KOREAN = /[가-힣]/;

function listSourceFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.') || entry.name === '__tests__') continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listSourceFiles(full));
    else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) && !full.includes(join('src', 'i18n'))) out.push(full);
  }
  return out;
}

const catalogSource = readFileSync(join(ROOT, 'src/i18n/translations.ts'), 'utf8');

/** 카탈로그 줄을 정규식으로 읽는다 — 파일을 import 하면 expo 쪽 의존이 딸려 온다. 열쇠는 작은따옴표 한 줄로 적혀 있다. */
const catalog = new Map();
for (const match of catalogSource.matchAll(/^\s*'((?:[^'\\]|\\.)*)':\s*\{(.*)\},?\s*(?:\/\/.*)?$/gm)) {
  const key = match[1].replace(/\\n/g, '\n').replace(/\\'/g, "'").replace(/\\\\/g, '\\');
  const body = match[2];
  // 🔴 빈 문자열('')도 «있다»로 친다 — 「님이」처럼 그 언어에 없는 말은 비우는 것이 답이다.
  catalog.set(key, {
    ja: /\bja:\s*'/.test(body),
    zhHans: /\bzhHans:\s*'/.test(body),
    zhHant: /\bzhHant:\s*'/.test(body),
  });
}

const NUMBER_RUN = /\d[\d,]*/g;

// 🔴 조사 헬퍼(src/i18n/korean.ts 의 koreanToward 등)가 낀 문구는 열쇠가 둘로 갈린다 — 「%s로 이동 중」「%s으로 이동 중」.
//    그 함수의 반환 타입('로' | '으로')을 읽어 두 열쇠를 다 본다. 헬퍼가 늘어도 여기를 고칠 필요가 없다.
const PARTICLE_HELPERS = new Map();
for (const match of readFileSync(join(ROOT, 'src/i18n/korean.ts'), 'utf8').matchAll(/export function (korean\w+)\([^)]*\):\s*((?:'[^']+'\s*\|\s*)+'[^']+')/g)) {
  PARTICLE_HELPERS.set(match[1], [...match[2].matchAll(/'([^']+)'/g)].map((m) => m[1]));
}
const missing = [];
const incomplete = [];
let checked = 0;

function check(key, where) {
  if (!KOREAN.test(key)) return;
  checked += 1;
  const entry = catalog.get(key);
  if (!entry) { missing.push(`${where}  ${JSON.stringify(key)}`); return; }
  const holes = ['ja', 'zhHans', 'zhHant'].filter((field) => !entry[field]);
  if (holes.length > 0) incomplete.push(`${where}  ${JSON.stringify(key)}  (빠진 언어: ${holes.join(' · ')})`);
}

function literalText(node) {
  return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : null;
}

/** tx(`사진 ${n}/3`, …) → 숫자를 뺀 모양 '사진 %d/%d'. pickLanguage 의 numericShape 와 같은 규칙이다. 조사 헬퍼가 끼면 열쇠가 여럿이다. */
function templateShapes(node) {
  let texts = [node.head.text];
  for (const span of node.templateSpans) {
    const call = span.expression;
    const helper = ts.isCallExpression(call) && ts.isIdentifier(call.expression) ? PARTICLE_HELPERS.get(call.expression.text) : undefined;
    const fillers = helper ?? ['%d'];
    texts = texts.flatMap((text) => fillers.map((filler) => text + filler + span.literal.text));
  }
  return texts.map((text) => text.replace(NUMBER_RUN, '%d'));
}

for (const dir of SCAN_DIRS) {
  for (const file of listSourceFiles(join(ROOT, dir))) {
    const rel = relative(ROOT, file).replace(/\\/g, '/');
    const sourceFile = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const visit = (node) => {
      const where = `${rel}:${sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1}`;
      if (ts.isCallExpression(node)) {
        const callee = node.expression;
        const name = ts.isIdentifier(callee) ? callee.text : ts.isPropertyAccessExpression(callee) ? callee.name.text : '';
        if ((name === 'tx' || name === 'txf') && node.arguments.length >= 2) {
          const ko = node.arguments[name === 'txf' ? 1 : 0];
          const fixed = literalText(ko);
          if (fixed !== null) check(fixed, where);
          else if (ts.isTemplateExpression(ko)) for (const shape of templateShapes(ko)) check(shape, `${where} (값이 끼는 문구 — 숫자면 이 모양을 표에, 글자면 txf 로)`);
        }
      }
      // { ko: '…', en: '…' } 꼴 데이터(지역 이름·질문 제목 등)도 pickLanguage 를 지난다 — 표에 없으면 똑같이 영어다.
      // ja 를 제 손으로 든 오브젝트(언어 이름표 같은 것)는 표를 안 거치므로 뺀다.
      if (ts.isObjectLiteralExpression(node)) {
        const props = new Map();
        for (const prop of node.properties) {
          if (!ts.isPropertyAssignment(prop)) continue;
          const key = ts.isIdentifier(prop.name) || ts.isStringLiteral(prop.name) ? prop.name.text : null;
          if (key) props.set(key, prop.initializer);
        }
        const ko = literalText(props.get('ko'));
        if (ko !== null && literalText(props.get('en')) !== null && !props.has('ja')) check(ko, where);
        // { labelKo: '…', labelEn: '…' } 처럼 이름 뒤에 Ko/En 이 붙은 짝 — S15P21E201-1520.
        // 이 모양으로 들고 있다가 tx(tool.labelKo, tool.labelEn) 로 부르면 위의 tx 검사는 «값»만
        // 봐서 못 잡는다. 챗봇 「대화 없이 바로 실행」 카드 부제 둘이 그렇게 일본어·중국어에서
        // 영어로 새었다(2026-09-23). 제 손으로 이름Ja 를 든 짝은 표를 안 거치므로 뺀다.
        for (const [name, init] of props) {
          const base = /^(.+)Ko$/.exec(name)?.[1];
          if (!base || props.has(`${base}Ja`)) continue;
          const text = literalText(init);
          if (text !== null && KOREAN.test(text) && literalText(props.get(`${base}En`)) !== null) check(text, where);
        }
      }
      // [ko, en] 튜플 — 앞이 한글, 뒤가 한글이 아닌 문자열.
      if (ts.isArrayLiteralExpression(node) && node.elements.length === 2) {
        const a = literalText(node.elements[0]);
        const b = literalText(node.elements[1]);
        if (a !== null && b !== null && KOREAN.test(a) && !KOREAN.test(b)) check(a, where);
      }
      ts.forEachChild(node, visit);
    };
    visit(sourceFile);
  }
}

// 스캐너가 헛돌면 이 검사가 조용히 통과한다. 그것도 실패로 친다.
if (checked < 500) {
  console.error(`🔴 앱에서 찾은 문구가 ${checked}개뿐입니다 — 스캐너가 tx 를 못 알아보는 것 같습니다.`);
  process.exit(1);
}

if (missing.length > 0 || incomplete.length > 0) {
  console.error('🔴 카탈로그에 없는 문구가 있습니다 (S15P21E201-1340). 일본어·중국어 화면에서 이 줄만 영어로 뜹니다.\n');
  for (const line of [...missing, ...incomplete]) console.error('  ' + line);
  console.error('\nsrc/i18n/translations.ts 에 그 한국어 원문을 키로 ja·zhHans·zhHant 를 채우세요.');
  console.error('값이 끼는 문구: 숫자만 끼면 숫자 자리를 %d 로 바꾼 모양을 올리고, 글자가 끼면 txf(tx, \'%s…\', \'%s…\', 값) 으로 바꾸세요.');
  process.exit(1);
}

console.log(`번역 검사 통과 (문구 ${checked}개, 세 언어 모두)`);
