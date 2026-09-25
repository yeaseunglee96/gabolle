// 둘러보기 넓은 화면 — 영어판에서 범위 토글(Nearby | All Busan)이 오른쪽 밖으로 밀렸다(S15P21E201-1703).
//
// 🔴 갈래 칩 묶음이 줄어들지 않았다. 앱 화면 틀(React Native)의 칸은 기본으로 안 줄어들어서, 칩이 한 줄로
//    끝까지 늘어나 토글을 밀어냈다. 한국어는 글자가 짧아 들어갔고, 영어는 1280 에서 「All Busan」이,
//    1024 에서는 「Nearby」까지 밖으로 나갔다. 자리만 틀리는 종류라 타입 검사·다른 시험이 전부 초록이다.

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/teamReportFixes.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

describe('둘러보기 넓은 화면 — 범위 토글이 늘 화면 안에 있다', () => {
  const source = read('app/explore.tsx');

  it('갈래 칩 묶음은 토글을 뺀 남는 폭만 쓰고, 넘치면 둘째 줄로 내려간다', () => {
    expect(source).toMatch(/\n  facetWrap: \{ flex: 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', gap: spacing\[2\] \},\n/);
  });

  it('토글은 제 폭(232)을 그대로 지킨다', () => {
    expect(source).toContain("scopeWide: { width: 232 },");
  });
});
