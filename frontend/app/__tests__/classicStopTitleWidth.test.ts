// 예전 일정 화면(`?classic=1`) — 폰에서 방문지 줄의 시간·자물쇠가 오른쪽 밖으로 밀려 잘렸다(S15P21E201-1701).
//
// 🔴 제목 덩이가 다른 곳과 같이 쓰는 `grow`(최소 폭 180)를 썼다. 폰 390 에서 들어갈 자리는
//    약 298 인데 사진 56 + 제목 180 + 시간·자물쇠 약 96 = 348 이라 50 이 넘쳤다.
//    화면은 그대로 그려지고 자리만 틀리는 종류라 타입 검사·다른 시험이 전부 초록이다.

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/teamReportFixes.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', relative), 'utf8') as string;
}

describe('예전 일정 화면 — 방문지 줄이 폰 폭 안에 들어간다', () => {
  const source = read('app/trips/[id]/itinerary.tsx');

  it('제목 덩이는 최소 폭 없이 남는 자리만 쓴다', () => {
    expect(source).toContain('onPress={onToggleExpand} style={styles.stopTitle}>');
    expect(source).toMatch(/\n  stopTitle: \{ flex: 1, minWidth: 0, gap: spacing\[1\] \},\n/);
  });

  it('순서 수정 안내 글은 그대로 최소 폭 180 을 쓴다 — 줄을 바꿔 아래로 내려가는 자리라서', () => {
    expect(source).toContain("style={styles.grow}>{tx('화살표로 순서를 바꾼 뒤 저장하세요.");
  });
});
