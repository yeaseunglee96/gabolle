// 「세 코스 비교는 준비 중이에요」를 보이지 않는다 — S15P21E201-1662.
//
// 🔴 이 시험이 지키는 것(사용자 결정): 코스가 하나일 때 여행 화면(폰·넓은)과 옛 추천 화면이 「세 코스 비교는 준비 중」을
//    말했다. 발표·심사에서 덜 만든 것처럼 보여서 뺐다. 세 곳 중 둘은 화면을 그리는 시험이 없어서, 소스에 그 문구가
//    남아 있으면 실패하게 한다 — 다시 들어와도 잡힌다.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');

function sources(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '__tests__') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, found);
    else if (/\.(ts|tsx)$/.test(entry.name)) found.push(full);
  }
  return found;
}

describe('코스 비교 「준비 중」 문구', () => {
  it('🔴 화면 코드와 번역표 어디에도 없다', () => {
    const hits = [...sources(path.join(ROOT, 'app')), ...sources(path.join(ROOT, 'src'))]
      .filter((file) => /코스 비교는 준비 중/.test(fs.readFileSync(file, 'utf8')))
      .map((file) => path.relative(ROOT, file));
    expect(hits).toEqual([]);
  });
});
