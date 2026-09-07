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
      const value = ts.isTemplateExpression(node) ? node.getText(sourceFile) : node.text;
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
