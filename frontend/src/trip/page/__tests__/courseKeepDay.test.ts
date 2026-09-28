declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

// 코스 A→B 로 바꾸면 일정 번호(loaded.id)가 바뀐다. 그때 보던 날을 1일차로 되돌리면 안 된다 (S15P21E201-1813).
const src: string = readFileSync(`${__dirname}/../useTripPage.ts`, 'utf8');

describe('코스를 바꿔도 보던 날을 유지한다', () => {
  it('일정 번호가 바뀔 때 dayIndex 를 0 으로 되돌리지 않는다', () => {
    expect(src).not.toMatch(/useEffect\(\(\) => \{ setDayIndex\(0\); \}, \[loaded\?\.id\]\)/);
  });
  it('새 코스가 더 짧으면 마지막 날로 맞춘다', () => {
    expect(src).toMatch(/setDayIndex\(\(prev\) => Math\.min\(prev, dayCount - 1\)\)/);
  });
  it('다른 여행을 열면 1일차부터 보인다', () => {
    expect(src).toMatch(/useEffect\(\(\) => \{ setDayIndex\(0\); \}, \[sourceKey\]\)/);
  });
});
