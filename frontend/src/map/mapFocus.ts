// 지도 아래쪽이 창에 가려 있을 때의 맞추기 · 가운데 잡기 (S15P21E201-1607).
//
// 🔴 폰 여행 화면은 지도를 줄이지 않고 창을 그 위에 겹쳐 올린다. 그러면 지도의 아래쪽 일부가 창 뒤에 숨는다.
//    지도 칸을 줄이는 대신 «가려진 높이(bottomInset)»를 지도에 알려서, 전체를 맞출 때는 그만큼 아래 여백을 더 두고
//    고른 곳은 «보이는 부분»의 가운데로 옮긴다. 앱의 지도(kakaoMapHtml.ts)도 같은 셈을 한다 — 그쪽은 문자열 안의
//    스크립트라 이 파일을 못 불러서 식을 한 번 더 적었다.

const EDGE = 60;
/** 번호 점(마커) 반지름 + 여유(px). 마커는 지름 34~40 이라 가운데가 가림 띠에서 이만큼은 떨어져야 다 보인다 — S15P21E201-1988. */
export const MARKER_CLEARANCE = 24;
/** 가림 띠를 빼고도 맞출 자리로 남겨 둘 최소 높이(px). 여백이 지도 높이를 다 먹으면 카카오가 동아시아 전체로 물러난다(S15P21E201-1903). */
const MIN_FIT_BAND = 48;
/** 넓은 화면 큰 지도 왼쪽 위의 「장소 N곳」 칩이 덮는 높이(top 12 + 높이 34 + 8) — TripPageDesktop. 범례가 있으면 LEGEND_COVER(80). */
export const BIG_MAP_CHIP_COVER = 54;

/**
 * 전체를 맞출 때의 여백 [위, 오른쪽, 아래, 왼쪽] — S15P21E201-1988.
 * 위는 «위에서 가린 높이(상태바·「장소 N곳」 칩·범례) + 점 반지름», 아래는 «아래에서 가린 높이(일정 창·탭 막대) + 점 반지름»으로
 * **따로** 둔다. 보이는 띠가 좁으면 그 띠에 맞춰 줌을 덜 당긴다 — 점이 가림 띠 뒤로 숨는 것보다 낫다.
 * 🔴 전에는 위아래 합이 넘치면 한쪽에서 덜어 냈다: 빌드 45 는 아래(창 윗변에 맨 아래 점이 걸림), 빌드 46 은 위(칩 뒤로 5번 점이 숨음).
 *    덜어 내는 쪽이 바뀌었을 뿐 늘 한쪽이 가렸다. 이제는 맞출 자리가 MIN_FIT_BAND 보다 좁아질 때만(지도 높이를 거의 다 덮었을 때) 줄인다.
 * mapWidth 는 예전 호출과 맞추려고 남겨 둔 인자다(쓰지 않는다).
 */
export function fitPadding(bottomInset: number, mapHeight: number, topInset = 0, _mapWidth = 0): [number, number, number, number] {
  let top = Math.max(EDGE, Math.max(0, topInset) + MARKER_CLEARANCE);
  let bottom = Math.max(EDGE, Math.max(0, bottomInset) + MARKER_CLEARANCE);
  if (mapHeight > 0) {
    let over = top + bottom + MIN_FIT_BAND - mapHeight;
    if (over > 0) { const cut = Math.min(over, bottom - MARKER_CLEARANCE); bottom -= cut; over -= cut; }
    if (over > 0) { top = Math.max(MARKER_CLEARANCE, top - over); }
  }
  return [top, EDGE, bottom, EDGE];
}

/**
 * 고른 곳을 보이는 부분의 가운데에 두려면 지도 중심을 얼마나 «아래로» 옮기나(px).
 * 보이는 부분의 가운데는 지도 가운데보다 (아래 가림 − 위 가림)의 절반만큼 위다.
 *
 * 🔴 위 가림(topInset)도 뺀다(S15P21E201-1896). 폰 여행 화면은 위에 요약 칩과 색 범례가 떠 있어서, 아래 가림만 보면 고른 곳이 범례 밑에
 *    깔렸다(열린 창 위로 보이는 지도가 약 190 뿐이라 가운데가 범례 자리와 겹친다). 음수는 만들지 않는다 — 위만 가려진 넓은 화면은
 *    전처럼 지도 가운데다.
 */
export function focusShiftY(bottomInset: number, mapHeight: number, topInset = 0): number {
  const room = Math.max(0, mapHeight - EDGE * 2);
  const bottom = Math.min(Math.max(0, bottomInset), room);
  const top = Math.min(Math.max(0, topInset), room);
  return Math.max(0, bottom - top) / 2;
}

/**
 * 장소를 고르면 이 줌(카카오 level, 작을수록 가깝다)까지는 확대한다 — S15P21E201-1903. 이미 더 가까우면 그대로 둔다.
 * 4 는 동네 골목이 보이는 줌이다. 앱의 지도(kakaoMapHtml.ts)도 같은 값을 쓴다.
 */
export const FOCUS_LEVEL = 4;

/**
 * 지도를 맞출 대상 — 번호 장소가 있으면 번호 장소만, 없으면 보이는 점 전부(주변 도움 지도처럼 점 표시만 있는 지도).
 * 🔴 출발지·숙소 같은 점 표시를 넣으면 먼 출발지(부산역) 때문에 해운대 네 곳이 한 점에 뭉쳤다(S15P21E201-1903, 실기기).
 */
export function fitTargets<T>(stops: readonly T[], visible: readonly T[]): readonly T[] {
  return stops.length ? stops : visible;
}

/**
 * 넓은 화면 여행 화면의 «큰 지도» 높이 — S15P21E201-1987.
 * 🔴 전에는 창 높이 − 260(최소 520)이라, 탭 가로(높이 ~750dp)에서는 지도 칸이 보이는 영역보다 아래로 길어져 맞춘 범위의
 *    아래쪽(4번·6번 점)이 첫 화면 밖에 있었다. 보이는 영역 높이(fillHeight — 스크롤 창 높이에서 본문 위치를 뺀 값)를 알면 그만큼만 쓴다.
 */
export function bigMapHeight(windowHeight: number, fillHeight: number): number {
  if (fillHeight > 0) return Math.max(320, Math.round(fillHeight));
  return Math.max(520, windowHeight - 260);
}
