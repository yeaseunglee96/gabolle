// 시험 안의 기다림이 시험 제한 시간보다 짧은가 — S15P21E201-1665.
//
// 🔴 이 시험이 지키는 것: 날씨 창 시험이 전체 실행 중 부하가 걸리면 가끔 실패했다. 실패 메시지는
//    「Exceeded timeout of 5000 ms for a test」. 첫 화면 그리기가 느려 화면을 최대 5초 기다리게(findByText timeout 5000)
//    해 두었는데, jest 시험 제한 시간이 기본 5초라 기다림이 끝나기 전에 시험이 먼저 끝났다 — 「5초까지 기다린다」가 먹히지 않았다.
//    기다림(timeout: N)이 기본 제한 시간 이상이면, 그 파일은 jest.setTimeout 으로 제한 시간을 기다림보다 길게 둬야 한다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const DEFAULT_TEST_TIMEOUT = 5000;

function testFiles(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) testFiles(full, found);
    else if (/\.test\.(ts|tsx|js)$/.test(entry.name)) found.push(full);
  }
  return found;
}

describe('시험 안의 기다림', () => {
  it('🔴 기본 제한 시간(5초) 이상 기다리는 파일은 제한 시간을 기다림보다 길게 둔다', () => {
    const problems = [];
    for (const file of [...testFiles(path.join(ROOT, 'app')), ...testFiles(path.join(ROOT, 'src'))]) {
      if (file === __filename) continue;
      const text = fs.readFileSync(file, 'utf8');
      const waits = [...text.matchAll(/timeout:\s*(\d{4,})/g)].map((m) => Number(m[1]));
      if (!waits.length) continue;
      const longest = Math.max(...waits);
      const limits = [...text.matchAll(/jest\.setTimeout\(\s*(\d+)\s*\)/g)].map((m) => Number(m[1]));
      const limit = limits.length ? Math.max(...limits) : DEFAULT_TEST_TIMEOUT;
      if (longest >= limit) problems.push(`${path.relative(ROOT, file)} — 기다림 ${longest}ms · 제한 시간 ${limit}ms`);
    }
    expect(problems).toEqual([]);
  });
});
