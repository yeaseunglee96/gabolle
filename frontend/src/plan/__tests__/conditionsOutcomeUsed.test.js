// 「저장하고 시작」이 시작까지 하는가 —-1334.
//
// 🔴 이 시험이 잡는 것은 **닫힘 값을 버리는 호출부**다. 조건 창은 닫히면서 무엇을 골랐는지
//    (SAVED · LATER · NEVER · DISMISSED)를 알려주는데, 그것을 안 보고 닫기만 하면 단추
//    이름이 「저장하고 시작」인데 **시작이 안 된다.** 창만 사라지고 같은 자리에 남고,
//    화면은 아무 말도 안 한다 — 실제로 배포된 화면에서 그렇게 멈춰 있었다.
//
// 🔴 **타입은 이걸 못 잡는다.** `(outcome) => void` 자리에 인자를 안 받는 함수를 넣는 것은
//    자바스크립트에서 정상이다. 그래서 글자로 본다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');

function sources(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '__tests__') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, found);
    else if (/\.tsx$/.test(entry.name)) found.push(full);
  }
  return found;
}

/** `<ConditionsPromptModal ... />` 한 덩어리를 통째로 집는다 — 여러 줄에 걸쳐 있다. */
function usages(text) {
  const found = [];
  let from = 0;
  for (;;) {
    const open = text.indexOf('<ConditionsPromptModal', from);
    if (open < 0) return found;
    const close = text.indexOf('/>', open);
    found.push(text.slice(open, close < 0 ? open + 400 : close));
    from = close < 0 ? open + 1 : close;
  }
}

describe('여행 조건 창', () => {
  const files = [...sources(path.join(ROOT, 'app')), ...sources(path.join(ROOT, 'src'))]
    .filter((file) => !file.endsWith('ConditionsPromptModal.tsx'))
    .filter((file) => fs.readFileSync(file, 'utf8').includes('<ConditionsPromptModal'));

  it('이 창을 쓰는 화면이 실제로 있다 — 없으면 아래 시험이 조용히 통과한다', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each([true])('🔴 닫힘 값을 버리는 화면이 없다 — 버리면 「저장하고 시작」이 시작을 안 한다', () => {
    const offenders = [];
    for (const file of files) {
      for (const usage of usages(fs.readFileSync(file, 'utf8'))) {
        const onClose = usage.match(/onClose=\{([\s\S]*)/);
        if (!onClose) { offenders.push(`${path.relative(ROOT, file)} → onClose 가 없다`); continue; }
        // 인자를 안 받는 화살표 함수 = 닫힘 값을 버린 것.
        if (/^\(\s*\)\s*=>/.test(onClose[1].trim())) {
          offenders.push(`${path.relative(ROOT, file)} → onClose={() => …} 로 값을 버린다`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it('🔴 질문 화면은 ✕ 로 닫은 것만 그 자리에 남긴다 — 건너뛰어도 길을 막지 않는다', () => {
    const text = fs.readFileSync(path.join(ROOT, 'app', '(plan)', 'questions.tsx'), 'utf8');
    expect(text).toContain("outcome !== 'DISMISSED'");
    // 🔴 돌아온 길에서는 조건을 다시 묻지 않는다. 다시 물으면 저장 → 열림 → 저장 → 열림이다.
    expect(text).toContain('afterConditions');
  });
});
