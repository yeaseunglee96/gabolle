// Button 의 containerStyle 에 색·모서리·테두리를 칠하는 것을 잡는다 (S15P21E201-1241).
//
// 🔴 왜 이게 결함인가
//
// Button 은 껍데기와 알맹이 두 겹이다.
//
//     <Animated.View style={containerStyle}>   ← 화면이 넘긴 스타일이 여기 붙는다
//       <Pressable style={[base, variant…]}>   ← 색과 모서리는 여기 있다
//
// 화면이 containerStyle 에 backgroundColor 를 칠하면, 안쪽 Pressable 은 자기 색을
// 그대로 그린다. 그래서 **둥근 버튼 뒤에 모서리가 안 깎인 같은 색 사각형이 하나 더**
// 남는다. 색이 같으면 "각진 버튼" 으로, 색이 다르면 "버튼 뒤로 다른 색이 삐져나옴" 으로
// 보인다. 후자는 S15P21E201-1151 에서 이미 한 번 잡았는데, 전자는 색이 같아 눈에 덜 띄어
// 그대로 남아 있었다. 2026-09-18 iOS 실기기 시험에서 「다음 버튼 모양 이상」으로 올라왔다.
//
// 고치는 법은 거의 언제나 "빼면 된다" 이다 — variant 가 이미 그 색을 갖고 있다.
//   primary=남색 · secondary · field · ghost · accent=주황 · danger
// 모양이 달라야 하면 `pill`(둥근 알약) 이나 `compact`(글자 폭) prop 을 쓴다.
//
// 이 첫째 검사는 **색·모서리·테두리만** 본다. width·minHeight 를 껍데기에 주는 것 자체는 막지 않는다 —
// 폭을 정해 주는 것은 괜찮은 쓰임이다. 폭이 «글자에 맞춰 줄어드는» 경우만 아래 둘째 검사가 잡는다.
//
// 🔴 둘째 검사 — 글자 폭으로 쪼그라든 버튼 (S15P21E201-1650)
//
// 알맹이(Pressable)는 언제나 껍데기를 꽉 채운다(width 100%). 그래서 껍데기가 글자 폭으로 줄어들면
// — width:'auto' 이거나 alignSelf 로 가운데·한쪽에 붙으면 — 색 면이 글자에 딱 붙는다. 껍데기에 준
// paddingHorizontal 은 색 면 «밖»이라 소용없다. 사람 눈에는 「버튼 좌우가 잘렸다」로 보인다.
// S15P21E201-1524 · 「남기기」(-1576) · 댓글 수정 「취소」「저장」(-1650) 세 번 나왔다.
// 고치는 법: `compact` prop(폭은 글자, 여백은 색 면 안쪽). 폭을 정해야 하면 width·minWidth 를 준다.
//
// 사용법: node tools/check-button-container.mjs
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';

const ROOT = join(decodeURIComponent(new URL('.', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');
const SCAN_DIRS = ['app', 'src'];

// 버튼 자신의 것이라 껍데기에 있으면 안 되는 속성.
const OWNED_BY_BUTTON = new Set(['backgroundColor', 'borderRadius', 'borderWidth', 'borderColor']);

// 🔴 아직 못 고친 자리. **늘리지 않는다 — 줄이는 목록이다.**
//
function listSourceFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listSourceFiles(full));
    else if (/\.tsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

/** `styles.foo` · `[styles.foo, styles.bar]` 에서 이름만 꺼낸다. */
function styleNames(expression) {
  if (!expression) return [];
  if (ts.isArrayLiteralExpression(expression)) return expression.elements.flatMap(styleNames);
  if (
    ts.isPropertyAccessExpression(expression)
    && ts.isIdentifier(expression.expression)
    && expression.expression.text === 'styles'
  ) return [expression.name.text];
  return [];
}

/** 파일 안 `StyleSheet.create({ … })` 의 최상위 항목을 이름 → 속성 목록으로 모은다. */
function collectStyleSheet(source) {
  const table = new Map();
  const visit = (node) => {
    if (
      ts.isCallExpression(node)
      && ts.isPropertyAccessExpression(node.expression)
      && node.expression.name.text === 'create'
      && node.arguments.length > 0
      && ts.isObjectLiteralExpression(node.arguments[0])
    ) {
      for (const entry of node.arguments[0].properties) {
        if (!ts.isPropertyAssignment(entry)) continue;
        const name = ts.isIdentifier(entry.name) || ts.isStringLiteral(entry.name) ? entry.name.text : null;
        if (!name || !ts.isObjectLiteralExpression(entry.initializer)) continue;
        table.set(name, entry.initializer);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return table;
}

function findings(file, source, shrunk) {
  const table = collectStyleSheet(source);
  const out = [];
  const visit = (node) => {
    const opening = ts.isJsxSelfClosingElement(node) ? node
      : ts.isJsxElement(node) ? node.openingElement
        : null;
    if (opening && ts.isIdentifier(opening.tagName) && opening.tagName.text === 'Button') {
      for (const attribute of opening.attributes.properties) {
        if (!ts.isJsxAttribute(attribute) || attribute.name.getText(source) !== 'containerStyle') continue;
        const initializer = attribute.initializer;
        if (!initializer || !ts.isJsxExpression(initializer)) continue;
        const names = styleNames(initializer.expression);
        const props = names.flatMap((name) => table.get(name)?.properties ?? [])
          .filter((p) => ts.isPropertyAssignment(p) && ts.isIdentifier(p.name));
        const value = (key) => props.filter((p) => p.name.text === key).map((p) => p.initializer.getText(source)).pop();
        const shrinks = value('width') === "'auto'" || ["'center'", "'flex-start'", "'flex-end'", "'baseline'"].includes(value('alignSelf'));
        const sized = (value('width') !== undefined && value('width') !== "'auto'") || value('minWidth') !== undefined || value('flex') !== undefined || value('flexGrow') !== undefined;
        const compact = opening.attributes.properties.some((a) => ts.isJsxAttribute(a) && a.name.getText(source) === 'compact');
        if (shrinks && !sized && !compact) {
          const { line } = source.getLineAndCharacterOfPosition(opening.getStart(source));
          shrunk.push({ name: names.join(' + '), line: line + 1 });
        }
        for (const name of names) {
          const literal = table.get(name);
          if (!literal) continue;
          const offenders = literal.properties
            .filter((p) => ts.isPropertyAssignment(p) && ts.isIdentifier(p.name) && OWNED_BY_BUTTON.has(p.name.text))
            .map((p) => p.name.getText(source));
          if (offenders.length === 0) continue;
          const { line } = source.getLineAndCharacterOfPosition(literal.getStart(source));
          out.push({ name, offenders, line: line + 1 });
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return out;
}

const problems = [];
const shrunkProblems = [];

for (const directory of SCAN_DIRS) {
  for (const file of listSourceFiles(join(ROOT, directory))) {
    const text = readFileSync(file, 'utf8');
    const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
    const shown = relative(ROOT, file).split('\\').join('/');
    const shrunk = [];
    const hits = findings(file, source, shrunk);
    for (const hit of shrunk) shrunkProblems.push(`  ${shown}:${hit.line}  styles.${hit.name} — 껍데기가 글자 폭으로 줄어드는데 compact·width·minWidth 가 없다`);
    for (const hit of hits) {
      const key = `${shown}:${hit.name}`;
      problems.push(`  ${shown}:${hit.line}  styles.${hit.name} 에 ${hit.offenders.join(' · ')}`);
    }
  }
}

if (problems.length > 0) {
  console.error('🔴 Button 의 containerStyle 에 버튼 자신의 것을 칠했습니다 (S15P21E201-1241).\n');
  console.error(problems.join('\n'));
  console.error('\ncontainerStyle 은 버튼 바깥 껍데기에 붙습니다. 색·모서리를 여기 칠하면');
  console.error('둥근 버튼 뒤에 각진 도형이 하나 더 남습니다 — 실기기에서 눈에 보입니다.');
  console.error('여백(margin)만 남기고 빼세요. 색은 variant 가, 모양은 pill·compact 가 갖습니다.');
  process.exit(1);
}

if (shrunkProblems.length > 0) {
  console.error('🔴 글자 폭으로 쪼그라든 버튼이 있습니다 (S15P21E201-1650).\n');
  console.error(shrunkProblems.join('\n'));
  console.error('\n버튼의 색 면은 껍데기를 꽉 채웁니다. 껍데기가 글자 폭이면 색 면도 글자에 딱 붙어 좌우가 잘려 보입니다.');
  console.error('껍데기에 준 paddingHorizontal 은 색 면 밖이라 소용없습니다. `compact` 를 쓰거나 width·minWidth 를 주세요.');
  process.exit(1);
}

console.log('Button containerStyle 검사 통과 — 예외로 남겨 둔 자리가 하나도 없습니다.');
