// 일정 편집 화면에서 순서를 바꾸고 돌아오면 여행 화면이 옛 순서를 그대로 보이던 것(S15P21E201-1984, 폴드 점검).
// 훅 전체를 띄우려면 지도·코스·인증을 다 흉내 내야 해서, 돌아올 때 다시 받는 장치가 그대로 있는지를 붙든다.
declare const __dirname: string;
declare const require: (id: string) => any;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const source: string = require('fs').readFileSync(require('path').join(__dirname, '../useTripPage.ts'), 'utf8');

test('화면에 돌아오면 같은 일정을 다시 받는다', () => {
  const block = source.slice(source.indexOf('if (!focusedOnce.current)'), source.indexOf('if (!focusedOnce.current)') + 300);
  expect(block).toContain('shownItineraryId.current = null');
  expect(block).toContain('setItineraryNonce((n) => n + 1)');
});

test('처음 열 때는 건너뛴다 — 처음 받기와 겹쳐 두 번 받지 않게', () => {
  expect(source).toMatch(/if \(!focusedOnce\.current\) \{ focusedOnce\.current = true; return; \}/);
});
