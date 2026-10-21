// 좁은 데스크톱 판에서 여행 화면 머리줄·코스 줄이 줄을 바꾼다 (S15P21E201-1953).
//
// 갤럭시 탭 세로(753dp)는 데스크톱 판이다. 일본어처럼 단추 글이 긴 언어에서 머리줄의 단추 다섯 개가 한 줄에
// 다 들어가려다 제목이 「햇살 가…」로 줄었고, 코스 줄의 「おすすめコース」 이름표는 한 글자씩 세로로 찌그러졌다.
// 배치는 jest 가 그리지 않으므로 스타일 약속을 소스에서 붙든다 — 이 저장소의 다른 배치 시험과 같은 방식이다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = readFileSync(join(__dirname, '..', 'TripPageDesktop.tsx'), 'utf8') as string;
const style = (name: string) => source.match(new RegExp(`\\n  ${name}: \\{([^}]*)\\}`))?.[1] ?? '';

describe('여행 화면 머리줄', () => {
  it('🔴 줄을 바꿀 수 있다 — 단추가 많아도 제목을 짓누르지 않는다', () => {
    expect(style('head')).toMatch(/flexWrap: 'wrap'/);
  });

  it('🔴 제목 칸은 360 아래로 줄지 않는다 — 모자라면 단추 묶음이 다음 줄로 간다', () => {
    // 240 이었을 때 탭 세로(753)에서 날짜 제목이 「10월 1일 (목) ...」로 잘렸다 — 단추 여섯이 한 줄에 들어가 버렸다(S15P21E201-1965).
    expect(style('headCopy')).toMatch(/flexBasis: 360/);
  });

  it('단추들은 한 묶음이다 — 하나씩 흩어져 내려가지 않는다', () => {
    expect(source).toMatch(/<View style=\{styles\.headActions\}>/);
    expect(style('headActions')).toMatch(/flexWrap: 'wrap'/);
  });
});

describe('코스 줄', () => {
  it('🔴 줄을 바꾸고, 「추천 코스」 이름표는 줄지 않는다 — 세로로 한 글자씩 찌그러졌다', () => {
    expect(style('courseRow')).toMatch(/flexWrap: 'wrap'/);
    expect(source).toMatch(/style=\{styles\.noShrink\}>\{tx\('추천 코스'/);
  });
});
