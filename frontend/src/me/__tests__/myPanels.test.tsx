// 겹쳐 여는 메뉴 전부.
//
// 🔴 -1331 — 예전에는 「옮긴 것」과 「아직인 것」을 갈랐다. 이제 **겹쳐 여는 것이 유일한
//    길이다**(하위 주소를 없앴다). 본문이 하나라도 비면 그 메뉴는 **빈 창이 열리고**,
//    화면은 멀쩡해 보여서 「내용이 없네」로 읽힌다.
import { panelTitle, isPanelKey, myPanelBody, type MyPanelKey } from '@/me/myPanels';

const ALL: MyPanelKey[] = [
  'posts', 'saved', 'saved-places', 'replies', 'followers', 'following', 'preferences',
  'identities', 'profile', 'notifications', 'blocked', 'help', 'terms',
];

const tx = (ko: string) => ko;

describe('마이페이지 패널', () => {
  it('🔴 전부 본문이 있다 — 빈 창이 열리지 않는다', () => {
    for (const key of ALL) {
      expect(myPanelBody(key)).toBeTruthy();
    }
  });

  it('전부 제목과 설명이 있다 — 머리가 빈 채로 열리지 않는다', () => {
    for (const key of ALL) {
      const { title, description } = panelTitle(key, tx);
      expect(title.length).toBeGreaterThan(0);
      expect(description.length).toBeGreaterThan(0);
    }
  });

  it('제목이 서로 다르다 — 같은 제목이 둘이면 어느 것을 열었는지 모른다', () => {
    const titles = ALL.map((key) => panelTitle(key, tx).title);
    expect(new Set(titles).size).toBe(titles.length);
  });

  it('🔴 밖에서 온 값이 아는 열쇠일 때만 창을 연다 — /me?panel=… 로 들어온다', () => {
    for (const key of ALL) {
      expect(isPanelKey(key)).toBe(true);
    }
  });

  it.each(['', 'POSTS', 'settings', '../posts', null, undefined, 7])(
    '🔴 모르는 값(%s)은 아무 창도 안 연다 — 주소로 아무거나 밀어 넣을 수 있는 자리다',
    (value) => {
      expect(isPanelKey(value)).toBe(false);
    });

  it('찾는 방법 자체가 살아 있다 — 없는 키는 본문이 없다', () => {
    // 이 줄이 없으면 hasPanel 이 늘 true 를 돌려줘도 위 시험이 통과할 수 있다.
    expect(myPanelBody('이런건없다' as MyPanelKey)).toBeUndefined();
  });
});
