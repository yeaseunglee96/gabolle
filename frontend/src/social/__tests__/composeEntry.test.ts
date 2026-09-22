import { composeEntryFor } from '../composeEntry';

// — 「어떤 폭에서도 글 쓸 입구가 하나는 있다」를 지킨다.
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

  // 🔴 1023/1024 는 떠 있는 단추가 뜨던 경계다. 그 단추는 이제 폭이 아니라 이 함수의
  //    답('headerButton')을 보고 뜨므로, 이 경계가 곧 「입구가 하나인가」의 경계다.
  it('경계 — 1023 까지도, 1024 부터도 맨 위 입력창이다', () => {
    expect(composeEntryFor(1023, true)).toBe('inline');
    expect(composeEntryFor(1024, true)).toBe('inline');
  });

  it('넓은 화면은 그대로 맨 위 입력창이다', () => {
    expect(composeEntryFor(1440, true)).toBe('inline');
    expect(composeEntryFor(1920, true)).toBe('inline');
  });

  it('비회원에게는 입구가 없다 — 로그인 안내가 대신 나온다', () => {
    for (const width of 폭들) expect(composeEntryFor(width, false)).toBe('none');
  });
});
