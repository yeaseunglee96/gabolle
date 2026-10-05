// 주변 버스 두 칸 화면(폴드)에서 「13분」이 「13 / 분」으로 꺾이던 것(S15P21E201-1984).
// 줄바꿈은 기기 글꼴 폭에서만 드러나 렌더 시험으로는 못 잡는다 — 그래서 꺾이지 않게 하는 두 장치가 남아 있는지 붙든다.
declare const __dirname: string;
declare const require: (id: string) => any;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const source: string = require('fs').readFileSync(require('path').join(__dirname, '../field/transit.tsx'), 'utf8');

test('도착 시각 글은 한 줄이고 줄어들지 않는다', () => {
  expect(source).toMatch(/numberOfLines=\{1\} style=\{styles\.arrivalTime\}/);
  expect(source).toMatch(/arrivalTime: \{ flexGrow: 1, flexShrink: 0 \}/);
});

test('줄어드는 쪽은 「몇 정류장 전」 글', () => {
  expect(source).toMatch(/style=\{styles\.stopsAway\}/);
  expect(source).toMatch(/stopsAway: \{ flexShrink: 1 \}/);
});
