// 화면 폭 경계값이 src/layout/breakpoints.ts 밖에서 숫자로 다시 생기는 것을 잡는다
// (S15P21E201-280). "width 비교에 쓰인 숫자 리터럴"을 찾는 방식이라, 그 숫자가
// 폭 경계값이 아니어도(예: 이미지 크기) width 라는 이름과 나란히 있으면 걸릴 수 있다 —
// 그런 자리는 breakpoint.sm/md/lg 나 isAtLeast() 로 옮기거나, 정말 폭 경계값이
// 아니라면 변수명에서 "width" 를 빼서 오탐을 피한다.
//
// 사용법: node tools/check-breakpoints.mjs
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');
const SCAN_DIRS = ['app', 'src'];
// 이 파일들이 경계값의 유일한 정의 자리다 — 여기서 숫자 리터럴을 쓰는 것은 정상이다.
const DEFINITION_FILES = ['src/layout/breakpoints.ts', 'src/layout/useLayout.ts'];
const COMPARISON_OPERATORS = new Set([
  ts.SyntaxKind.LessThanToken,
  ts.SyntaxKind.LessThanEqualsToken,
  ts.SyntaxKind.GreaterThanToken,
  ts.SyntaxKind.GreaterThanEqualsToken,
]);

function listSourceFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...listSourceFiles(full));
    } else if (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx')) {
      out.push(full);
    }
  }
  return out;
}

function lineOf(sourceFile, pos) {
  return sourceFile.getLineAndCharacterOfPosition(pos).line + 1;
}

function mentionsWidth(node) {
  const text = node.getText().toLowerCase();
  return text.includes('width');
}

function checkFile(filePath) {
  const text = readFileSync(filePath, 'utf8');
  const sourceFile = ts.createSourceFile(filePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const findings = [];

  function visit(node) {
    if (ts.isBinaryExpression(node) && COMPARISON_OPERATORS.has(node.operatorToken.kind)) {
      const { left, right } = node;
      const literalSide = ts.isNumericLiteral(left) ? left : ts.isNumericLiteral(right) ? right : null;
      const otherSide = literalSide === left ? right : left;
      if (literalSide && mentionsWidth(otherSide)) {
        findings.push({ line: lineOf(sourceFile, node.getStart(sourceFile)), text: node.getText(sourceFile).trim().slice(0, 100) });
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return findings;
}

const files = SCAN_DIRS.flatMap((dir) => listSourceFiles(join(ROOT, dir)))
  .filter((file) => !DEFINITION_FILES.includes(relative(ROOT, file).replace(/\\/g, '/')));

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
  console.error(`\n🔴 폭 경계값이 breakpoints.ts 밖에서 숫자로 ${totalFindings}건 — breakpoint.sm/md/lg 나 isAtLeast() 를 쓴다.`);
  process.exit(1);
}

console.log(`검사한 파일 ${files.length}개 — breakpoints.ts 밖의 폭 경계값 없음.`);
