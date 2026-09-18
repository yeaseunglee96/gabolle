// 겹쳐 열 수 있는 메뉴와 아직인 메뉴.
//
// 🔴 이 목록을 잘못 켜면 빈 패널이 열린다. 화면은 멀쩡해 보이고 「내용이 없네」로 읽히는데,
//    실제로는 옮기지도 않은 것을 열어 둔 것이다. 그래서 켠 것과 안 켠 것을 둘 다 고정한다.
import { panelTitle, hasPanel, myPanelBody, type MyPanelKey } from '@/me/myPanels';

const ALL: MyPanelKey[] = [
  'posts', 'saved', 'followers', 'following', 'preferences',
  'identities', 'profile', 'notifications', 'blocked', 'help', 'terms',
];

/** 지금까지 옮긴 것. 하나 옮길 때마다 여기에 더한다. */
const MIGRATED: MyPanelKey[] = ['blocked', 'followers', 'following', 'help', 'identities', 'notifications', 'terms'];

const tx = (ko: string) => ko;

describe('마이페이지 패널', () => {
  it('🔴 옮긴 것만 겹쳐 열린다 — 나머지는 지금처럼 화면이 바뀐다', () => {
    const open = ALL.filter(hasPanel);
    expect(open.sort()).toEqual([...MIGRATED].sort());
  });

  it('🔴 옮긴 것은 본문이 진짜로 있다 — 빈 패널이 열리지 않는다', () => {
    for (const key of MIGRATED) {
      expect(myPanelBody(key)).not.toBeNull();
    }
  });

  it('열한 개 전부 제목과 설명이 있다 — 머리가 빈 채로 열리지 않는다', () => {
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

  it('찾는 방법 자체가 살아 있다 — 안 옮긴 것은 null 이다', () => {
    // 이 줄이 없으면 hasPanel 이 늘 true 를 돌려줘도 위 시험이 통과할 수 있다.
    expect(myPanelBody('posts')).toBeNull();
    expect(hasPanel('posts')).toBe(false);
    expect(hasPanel('saved')).toBe(false);
    expect(hasPanel('preferences')).toBe(false);
    expect(hasPanel('profile')).toBe(false);
  });
});
