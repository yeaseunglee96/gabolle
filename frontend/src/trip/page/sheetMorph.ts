// 여행 화면(폰) 탭바 = 창 — 막대가 제자리에서 늘어나 창이 되는 모양 셈(S15P21E201-1756).
//
// 막대(폭 328 · 높이 64)와 창(폭 화면-32 · 높이 화면-창 위)은 바닥 여백과 모서리(20)를 같이 쓴다.
// 그래서 바닥은 그대로 두고 폭·높이만 자라면 «떠 있는 막대가 커져 창이 된다». 밑에서 밀려 올라오지 않는다.
// 이 함수는 Reanimated 가 폰 쪽(UI 스레드)에서 매 프레임 부른다 — 'worklet' 표시가 그것이다. 시험에서는 그냥 함수다.

export type Size = { width: number; height: number };

/** 탭 줄은 자라기 시작하면 곧(이 진행도까지) 흐려진다 — 늘어나는 막대 안에 탭이 남아 늘어져 보이지 않게. */
export const TABS_OUT = 0.3;
/** 창 속은 커지는 끝 무렵(이 구간에서) 나타난다 — 다 자란 크기로 바닥에 붙여 둔 속이 자라는 동안 비치지 않게. */
//    곡선이 앞에서 빨리 자라므로(0.2,0.8,0.2,1) 0.85 도 시간으로는 절반쯤이다 — 더 이르면 반쯤 자란 창에 속이 비쳤다(실측 80ms).
export const CONTENT_IN: readonly [number, number] = [0.85, 1];

/** 진행도 g(0 = 막대, 1 = 창)의 크기. 곡선이 튀어도 막대·창 밖으로 나가지 않게 0~1 로 자른다. */
export function morphSize(g: number, from: Size, to: Size): Size {
  'worklet';
  const t = Math.min(1, Math.max(0, g));
  return {
    width: Math.round(from.width + (to.width - from.width) * t),
    height: Math.round(from.height + (to.height - from.height) * t),
  };
}
