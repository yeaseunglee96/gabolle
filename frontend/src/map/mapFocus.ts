// 지도 아래쪽이 창에 가려 있을 때의 맞추기 · 가운데 잡기 (S15P21E201-1607).
//
// 🔴 폰 여행 화면은 지도를 줄이지 않고 창을 그 위에 겹쳐 올린다. 그러면 지도의 아래쪽 일부가 창 뒤에 숨는다.
//    지도 칸을 줄이는 대신 «가려진 높이(bottomInset)»를 지도에 알려서, 전체를 맞출 때는 그만큼 아래 여백을 더 두고
//    고른 곳은 «보이는 부분»의 가운데로 옮긴다. 앱의 지도(kakaoMapHtml.ts)도 같은 셈을 한다 — 그쪽은 문자열 안의
//    스크립트라 이 파일을 못 불러서 식을 한 번 더 적었다.

const EDGE = 60;

/** 전체를 맞출 때의 여백 [위, 오른쪽, 아래, 왼쪽]. 아래는 가려진 만큼 더 — 다만 지도에 보일 자리(위아래 120)는 남긴다. */
export function fitPadding(bottomInset: number, mapHeight: number): [number, number, number, number] {
  const bottom = Math.min(EDGE + Math.max(0, bottomInset), Math.max(EDGE, mapHeight - EDGE * 2));
  return [EDGE, EDGE, bottom, EDGE];
}

/**
 * 고른 곳을 보이는 부분의 가운데에 두려면 지도 중심을 얼마나 «아래로» 옮기나(px).
 * 보이는 부분의 가운데는 지도 가운데보다 가려진 높이의 절반만큼 위다.
 */
export function focusShiftY(bottomInset: number, mapHeight: number): number {
  return Math.max(0, Math.min(bottomInset, mapHeight - EDGE * 2)) / 2;
}
