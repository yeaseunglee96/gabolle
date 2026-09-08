// 영어 모드에 남는 한글을 자동으로 찾는다 (S15P21E201-256).
//
// 실제로 화면을 렌더링해서 찾는 방식(로그인 → 각 화면 진입 → 영어로 전환 → DOM 훑기)은
// 인증이 걸린 화면마다 실 계정과 백엔드가 있어야 해서, 이 검사 하나가 매번 배포된
// 백엔드 상태에 좌우된다. 대신 소스에서 "화면 코드(.tsx)에 있는 문자열 리터럴 중
// useI18n()의 tx(ko, en) 호출 인자가 아닌 자리에 있는 한글"을 찾는다 — 이 프로젝트의
// 실제 규칙이 "한글은 전부 tx() 를 거친다"이기 때문에, tx() 밖에 있는 한글은 거의
// 항상 새로고침 없이 번역 안 된 채 남는 자리다. 화면 코드(app/**/*.tsx, src/**/*.tsx)만
// 본다 — 서비스 계층(.ts)의 메시지 문자열은 이 화면 단위 규칙과 별개 문제라 범위 밖이다.
//
// 사용법: node tools/i18n-check.mjs
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');
const SCAN_DIRS = ['app', 'src'];
const KOREAN = /[가-힣]/;
// "해운대 해수욕장 (Haeundae Beach)" 처럼 한글 바로 뒤에 괄호 영문이 붙는 병기 표기는 예외.
const BILINGUAL_PAIR = /[가-힣][^()\n]{0,60}\([A-Za-z][^()\n]{0,80}\)/;

function listTsxFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...listTsxFiles(full));
    } else if (entry.name.endsWith('.tsx')) {
      out.push(full);
    }
  }
  return out;
}

function isTxCall(node) {
  if (!ts.isCallExpression(node)) return false;
  const callee = node.expression;
  if (ts.isIdentifier(callee)) return callee.text === 'tx';
  if (ts.isPropertyAccessExpression(callee)) return callee.name.text === 'tx';
  return false;
}

function lineOf(sourceFile, pos) {
  return sourceFile.getLineAndCharacterOfPosition(pos).line + 1;
}

function stringValue(sourceFile, node) {
  if (ts.isStringLiteralLike(node)) return node.text;
  if (ts.isTemplateExpression(node)) return node.getText(sourceFile);
  return null;
}

// 🔴 이 검사기는 `tx(...)` 호출의 "직접 인자"만 덮인 것으로 본다. 그런데 이 프로젝트의
// 실제 관행은 한/영 쌍을 시드 배열·상수 객체(`{ labelKo, labelEn }`, `[ko, en]` 튜플)로
// 먼저 만들고 렌더링할 때 `tx(item.labelKo, item.labelEn)` 나 `tx(...pair)` 처럼 "간접"으로
// 넘기는 것이다. 그 간접 참조를 못 따라가면 이미 tx() 를 거치는 값을 전부 오탐으로 잡는다
// (TabBar 의 TABS, PlanStepHeader 의 STEPS, 법률 문서 화면의 title/lead 튜플 등).
// 완전한 데이터 흐름 추적 대신, 이 프로젝트가 실제로 쓰는 두 모양만 알아본다:
//   1) 오브젝트 리터럴 안에서 접두사가 같고 "Ko"/"En" 로 끝나는 프로퍼티 쌍 (예: labelKo/labelEn)
//   2) 원소 2개짜리 배열 리터럴에서 앞이 한글 문자열, 뒤가 한글이 아닌 문자열인 튜플 (예: [ko, en])
// 이 두 모양이 아닌, 진짜로 tx() 밖에 있는 한글은 그대로 잡는다.
// "Ko"/"En" 이 키의 접미사(labelKo/labelEn)인 자리도, 접두사(koDesc/enDesc)인 자리도
// 이 프로젝트에 둘 다 있다 — 어느 쪽이든 떼어내고 남는 "핵심 이름"이 같으면 짝으로 본다.
function splitKoEnKey(key) {
  if (/ko$/i.test(key)) return { lang: 'ko', core: key.slice(0, -2) };
  if (/en$/i.test(key)) return { lang: 'en', core: key.slice(0, -2) };
  if (/^ko/i.test(key)) return { lang: 'ko', core: key.slice(2) };
  if (/^en/i.test(key)) return { lang: 'en', core: key.slice(2) };
  return null;
}

function markBilingualPairsCovered(sourceFile, node, markCovered) {
  if (ts.isObjectLiteralExpression(node)) {
    const byPrefix = new Map();
    for (const prop of node.properties) {
      if (!ts.isPropertyAssignment(prop)) continue;
      const name = prop.name;
      const key = ts.isIdentifier(name) || ts.isStringLiteral(name) ? name.text : null;
      if (!key) continue;
      const split = splitKoEnKey(key);
      if (!split) continue;
      const entry = byPrefix.get(split.core) ?? {};
      entry[split.lang] = prop.initializer;
      byPrefix.set(split.core, entry);
    }
    for (const { ko, en } of byPrefix.values()) {
      if (!ko || !en) continue;
      const koVal = stringValue(sourceFile, ko);
      const enVal = stringValue(sourceFile, en);
      if (koVal != null && enVal != null && KOREAN.test(koVal) && !KOREAN.test(enVal)) {
        markCovered(ko);
      }
    }
  }
  // 같은 Ko/En 짝이 오브젝트 프로퍼티가 아니라 함수 매개변수 기본값으로도 나온다
  // (예: `(tx, values, emptyKo = '해당 없음', emptyEn = 'None') => ... tx(emptyKo, emptyEn)`).
  if (ts.isFunctionLike(node) && node.parameters) {
    const byPrefix = new Map();
    for (const param of node.parameters) {
      if (!ts.isIdentifier(param.name) || !param.initializer) continue;
      const split = splitKoEnKey(param.name.text);
      if (!split) continue;
      const entry = byPrefix.get(split.core) ?? {};
      entry[split.lang] = param.initializer;
      byPrefix.set(split.core, entry);
    }
    for (const { ko, en } of byPrefix.values()) {
      if (!ko || !en) continue;
      const koVal = stringValue(sourceFile, ko);
      const enVal = stringValue(sourceFile, en);
      if (koVal != null && enVal != null && KOREAN.test(koVal) && !KOREAN.test(enVal)) {
        markCovered(ko);
      }
    }
  }
  // 길이 2짜리 [ko, en] 튜플뿐 아니라, `['HAEUNDAE', '해운대', 'Haeundae']` 처럼 앞에
  // 코드값이 붙는 3개짜리 튜플도, `names(tx, values, '선택 안 함', 'None selected')` 처럼
  // 호출 인자 자리에 바로 놓이는 것도 이 프로젝트에 있다 — 자리 수·문맥을 못박지 않고,
  // "한글 문자열 바로 다음이 한글 아닌 문자열"인 인접 쌍이면 앞쪽을 덮인 것으로 본다.
  const positional = ts.isArrayLiteralExpression(node) ? node.elements : ts.isCallExpression(node) ? node.arguments : null;
  if (positional) {
    for (let i = 0; i < positional.length - 1; i += 1) {
      const a = positional[i];
      const b = positional[i + 1];
      const aVal = stringValue(sourceFile, a);
      const bVal = stringValue(sourceFile, b);
      if (aVal != null && bVal != null && KOREAN.test(aVal) && !KOREAN.test(bVal)) {
        markCovered(a);
      }
    }
  }
}

function checkFile(filePath) {
  const text = readFileSync(filePath, 'utf8');
  const sourceFile = ts.createSourceFile(filePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const covered = []; // tx(...) 호출의 인자 범위 — 이 안의 한글은 정상이다.
  const findings = [];

  function markCovered(node) {
    covered.push([node.getStart(sourceFile), node.getEnd()]);
  }

  function isCovered(node) {
    const start = node.getStart(sourceFile);
    return covered.some(([s, e]) => start >= s && start < e);
  }

  function visit(node) {
    if (isTxCall(node)) {
      node.arguments.forEach(markCovered);
    }
    // encodeURIComponent(...) 인자는 URL 로 인코딩되는 값이라 화면에 그대로 보이는 문구가
    // 아니다 — 택시 카드의 주소 링크가 그렇다. 번역 대상이 아니므로 덮인 것으로 본다.
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'encodeURIComponent') {
      node.arguments.forEach(markCovered);
    }
    markBilingualPairsCovered(sourceFile, node, markCovered);
    if (ts.isJsxText(node)) {
      // JSX 순수 텍스트 자식(예: <Text>안녕하세요</Text>)은 tx() 호출 자체가 될 수 없어
      // covered 검사가 필요 없다 — 한글이 있으면 그대로 남는 자리다.
      const value = node.getText(sourceFile).trim();
      if (KOREAN.test(value) && !BILINGUAL_PAIR.test(value)) {
        findings.push({ line: lineOf(sourceFile, node.getStart(sourceFile)), text: value.slice(0, 80) });
      }
    } else if (
      (ts.isStringLiteralLike(node) || ts.isTemplateExpression(node)) &&
      !ts.isLiteralTypeNode(node.parent) &&
      !(ts.isImportDeclaration(node.parent) && node.parent.moduleSpecifier === node)
    ) {
      // 템플릿의 "고정 글자" 부분만 본다. `${...}` 안 표현식은 별도 노드로 이미 따로
      // 검사되므로, 여기서 전체 원문(getText)을 다시 훑으면 이미 덮인 하위 리터럴
      // (예: `${encodeURIComponent('...')}`) 의 한글이 바깥 템플릿 노드에서 또 잡힌다.
      const value = ts.isTemplateExpression(node)
        ? node.head.text + node.templateSpans.map((span) => span.literal.text).join('')
        : node.text;
      if (KOREAN.test(value) && !BILINGUAL_PAIR.test(value) && !isCovered(node)) {
        findings.push({ line: lineOf(sourceFile, node.getStart(sourceFile)), text: value.trim().slice(0, 80) });
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return findings;
}

const files = SCAN_DIRS.flatMap((dir) => listTsxFiles(join(ROOT, dir)));
let totalFindings = 0;

for (const file of files) {
  const findings = checkFile(file);
  if (!findings.length) continue;
  totalFindings += findings.length;
  console.log(relative(ROOT, file));
  for (const finding of findings) {
    console.log(`  ${finding.line}: ${finding.text}`);
  }
}

if (totalFindings > 0) {
  console.error(`\n🔴 tx() 밖에 있는 한글 ${totalFindings}건 — 영어 모드에서 그대로 남는다.`);
  process.exit(1);
}

console.log(`검사한 파일 ${files.length}개 — tx() 밖의 한글 없음.`);
