// 웹 홈의 큰 검색창 ↔ 위쪽 메뉴의 작은 알약 — 둘이 함께 보는 값(S15P21E201-1931).
//
// 🔴 스크롤에 맞춰 큰 검색창이 줄어들며 사라지고, 같은 만큼 위쪽 메뉴 가운데의 알약이 커지며 나타난다(사용자 요청 2026-10-02,
//    에어비앤비 영상 — 「흰 칸이 생기는 게 아니라 검색창 자체가 작아지면서」). 그래서 값은 «켜짐/꺼짐»이 아니라 0~1 의 진행도다.
//    스크롤마다 React 상태를 바꾸면 위쪽 메뉴가 통째로 다시 그려진다 — 움직임 값(Animated.Value) 하나를 같이 쥔다.
import { Animated } from 'react-native';
import { useSyncExternalStore } from 'react';

/** 0 = 큰 검색창 그대로, 1 = 위쪽 메뉴 알약으로 다 접힘 */
export const searchCollapse = new Animated.Value(0);

/** 알약에 쓰는 글 — 큰 검색창에서 고른 값. 안 골랐으면 null 이고 알약은 빈 칸 안내(「어디서 출발」)를 쓴다 */
export type PillLabels = { origin: string | null; dates: string | null; people: string | null };
/** expanded: 알약을 눌러 큰 검색창을 위 막대 아래로 펼친 동안 — 알약은 숨는다(같은 검색창을 두 번 그리지 않게) */
type Handle = { active: boolean; collapsed: boolean; open: (() => void) | null; labels?: PillLabels | null; expanded?: boolean };
let handle: Handle = { active: false, collapsed: false, open: null, labels: null };
const listeners = new Set<() => void>();

/** 홈이 부른다 — active: 이 화면이 알약을 쓰는가, collapsed: 지금 알약을 누를 수 있나(반 넘게 접혔나) */
export function setSearchHandle(next: Handle): void {
  const labels = next.labels === undefined ? handle.labels : next.labels;
  const expanded = next.expanded === undefined ? handle.expanded ?? false : next.expanded;
  if (next.active === handle.active && next.collapsed === handle.collapsed && next.open === handle.open && sameLabels(labels, handle.labels) && expanded === (handle.expanded ?? false)) return;
  handle = { ...next, labels, expanded };
  listeners.forEach((listener) => listener());
}

function sameLabels(a: PillLabels | null | undefined, b: PillLabels | null | undefined): boolean {
  if (!a || !b) return !a && !b;
  return a.origin === b.origin && a.dates === b.dates && a.people === b.people;
}

/** 지금 값 — 일부만 바꿔 다시 적을 때 */
export function searchHandleNow(): Handle {
  return handle;
}

export function useSearchHandle(): Handle {
  return useSyncExternalStore((listener) => { listeners.add(listener); return () => listeners.delete(listener); }, () => handle, () => handle);
}

/** 스크롤 위치 → 진행도. 검색창 아래 끝 앞 `span` 만큼에서 0→1 로 간다. 자리를 못 쟀으면 0 */
export function collapseProgress(scrollY: number, searchBottom: number | null, span = 140): number {
  if (searchBottom === null || searchBottom <= 0) return 0;
  const start = searchBottom - span;
  if (scrollY <= start) return 0;
  if (scrollY >= searchBottom) return 1;
  return (scrollY - start) / span;
}
