import { composeEntryFor } from '../composeEntry';

// S15P21E201-1142 — 「어떤 폭에서도 글 쓸 입구가 하나는 있다」를 지킨다.
//
// 🔴 전에는 입구 조건이 두 곳에 나뉘어 있었고 그 사이가 비어 있었다.
//   헤더 「기록」 버튼 : compact = 폭 ≤ 599
//   맨 위 입력창      : wide    = 폭 > 1023
//   → 600~1023 에 아무것도 없었다. 브라우저를 125% 로 확대한 데스크톱이 딱 여기 든다.
describe('피드 글쓰기 입구', () => {
  const 폭들 = [320, 375, 414, 599, 600, 768, 800, 980, 1023, 1024, 1180, 1440, 1920, 2560];

  it('🔴 로그인한 사람에게는 어떤 폭에서도 입구가 있다', () => {
    const 없는폭 = 폭들.filter((width) => composeEntryFor(width, true) === 'none');
    expect(없는폭).toEqual([]);
  });

  it('🔴 구멍이었던 600~1023 에서 맨 위 입력창이 나온다', () => {
    expect(composeEntryFor(600, true)).toBe('inline');
    expect(composeEntryFor(800, true)).toBe('inline');
    expect(composeEntryFor(980, true)).toBe('inline');
    expect(composeEntryFor(1023, true)).toBe('inline');
  });

  it('폰 폭에서는 전체 화면 글쓰기로 보낸다 — 목록이 밀려 내려가지 않게', () => {
    expect(composeEntryFor(320, true)).toBe('headerButton');
    expect(composeEntryFor(599, true)).toBe('headerButton');
  });

  it('넓은 화면은 그대로 맨 위 입력창이다', () => {
    expect(composeEntryFor(1440, true)).toBe('inline');
    expect(composeEntryFor(1920, true)).toBe('inline');
  });

  it('비회원에게는 입구가 없다 — 로그인 안내가 대신 나온다', () => {
    for (const width of 폭들) expect(composeEntryFor(width, false)).toBe('none');
  });
});
