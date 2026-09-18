// 겹쳐 열 수 있는 메뉴와 아직인 메뉴.
//
// 🔴 이 목록을 잘못 켜면 빈 패널이 열린다. 화면은 멀쩡해 보이고 「내용이 없네」로 읽히는데,
//    실제로는 옮기지도 않은 것을 열어 둔 것이다. 그래서 켠 것과 안 켠 것을 둘 다 고정한다.
import { panelTitle, hasPanel, myPanelBody, type MyPanelKey } from '@/me/myPanels';

const ALL: MyPanelKey[] = [
  'posts', 'saved', 'followers', 'following', 'preferences',
  'identities', 'profile', 'notifications', 'blocked', 'help', 'terms',
];

/** 지금까지 옮긴 것 — 열한 개 전부. */
const MIGRATED: MyPanelKey[] = [...ALL];

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

  it('🔴 열한 개가 전부 옮겨졌다 — 빠진 것이 없다', () => {
    // 이제 화면을 바꾸는 메뉴는 없다. 하나라도 null 이면 그 메뉴만 옛 방식으로 열린다.
    const missing = ALL.filter((key) => !hasPanel(key));
    expect(missing).toEqual([]);
  });

  it('찾는 방법 자체가 살아 있다 — 없는 키는 본문이 없다', () => {
    // 이 줄이 없으면 hasPanel 이 늘 true 를 돌려줘도 위 시험이 통과할 수 있다.
    expect(myPanelBody('이런건없다' as MyPanelKey)).toBeUndefined();
  });
});
