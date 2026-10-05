// 지도 아래쪽이 창에 가려 있을 때의 맞추기 · 가운데 잡기 (S15P21E201-1607).
//
// 🔴 폰 여행 화면은 지도를 줄이지 않고 창을 그 위에 겹쳐 올린다. 그러면 지도의 아래쪽 일부가 창 뒤에 숨는다.
//    지도 칸을 줄이는 대신 «가려진 높이(bottomInset)»를 지도에 알려서, 전체를 맞출 때는 그만큼 아래 여백을 더 두고
//    고른 곳은 «보이는 부분»의 가운데로 옮긴다. 앱의 지도(kakaoMapHtml.ts)도 같은 셈을 한다 — 그쪽은 문자열 안의
//    스크립트라 이 파일을 못 불러서 식을 한 번 더 적었다.

const EDGE = 60;
/** 전체를 맞출 때 여백을 빼고도 남겨 둘 지도 높이(px) — 이보다 좁게 맞추면 너무 멀리 물러난다. */
const MIN_FIT_ROOM = EDGE;
/** 가로로 넓은 지도에서 맞출 자리로 남길 높이 비율 — S15P21E201-1980. */
const WIDE_MIN_ROOM = 0.45;
/** 세로 지도(폰·폴드 바깥 화면)에서 맞출 자리로 남길 높이 비율 — S15P21E201-1986. 60px 띠에 맞추면 번호 점이 한 덩어리가 됐다. */
const TALL_MIN_ROOM = 0.25;

/**
 * 전체를 맞출 때의 여백 [위, 오른쪽, 아래, 왼쪽]. 아래는 가려진 만큼 더 — 다만 지도에 보일 자리(위아래 120)는 남긴다.
 * 위도 가려진 만큼(topInset — 상태바와 지도 위에 뜬 칩) 더 둔다(S15P21E201-1754). 전에는 위가 늘 60 이라
 * 폰에서 출발지·정차지가 상태바와 「장소 N곳」·지도 위 칩(지금은 색 범례) 밑으로 숨었다.
 */
export function fitPadding(bottomInset: number, mapHeight: number, topInset = 0, mapWidth = 0): [number, number, number, number] {
  let top = EDGE + Math.max(0, topInset);
  let bottom = Math.min(EDGE + Math.max(0, bottomInset), Math.max(EDGE, mapHeight - top - EDGE));
  // 🔴 위아래 여백이 지도 높이를 다 먹으면 카카오가 범위를 못 맞춰 동아시아 전체로 물러났다(S15P21E201-1903 — 폰을 가로로
  //    돌렸을 때: 높이 ~410 에 위 칩·범례와 아래 창이 거의 다 덮었다). 맞출 자리를 MIN_FIT_ROOM 만큼은 남기게 여백을 줄인다.
  // 🔴 넓고 낮은 지도(탭·웹 가로)는 60px 띠에 부산 전체를 맞추면 통영·거제까지 물러났다(S15P21E201-1980). 가로일 때는
  //    높이의 WIDE_MIN_ROOM 만큼을 맞출 자리로 남긴다 — 점 몇 개가 창 뒤로 가도 도시 단위 줌이 낫다. 세로(폰)는 전과 같다.
  const wide = mapWidth > mapHeight * 1.3;
  // 🔴 세로 지도도 60px 띠에 맞추면 너무 멀어졌다(S15P21E201-1986, 폴드 바깥 화면 — 아래 창이 높이의 대부분을 덮어 김해공항~오륙도가
  //    한 화면, 번호 점 1~6 이 한 덩어리). 세로는 높이의 TALL_MIN_ROOM 만큼을 맞출 자리로 남긴다 — 가장자리 점이 창 뒤로 가도 동네 단위 줌이 낫다.
  const room = Math.max(0, mapHeight - Math.max(MIN_FIT_ROOM, Math.round(mapHeight * (wide ? WIDE_MIN_ROOM : TALL_MIN_ROOM))));
  if (mapHeight > 0 && top + bottom > room) {
    // 🔴 넘친 만큼은 위 여백(상태바·칩 — 반투명)에서 먼저 덜어 내고, 위가 EDGE 까지 줄어든 뒤에야 둘을 같은 비율로 줄인다(S15P21E201-1987).
    //    전에는 둘을 같은 비율로 줄여 아래 여백이 아래 창 높이보다 훨씬 작아졌고, 맨 아래 번호 점이 불투명한 창 윗변에 반쯤 걸렸다(폴드 바깥 화면, 빌드 45).
    const fromTop = Math.min(top + bottom - room, Math.max(0, top - EDGE));
    top -= fromTop;
    if (top + bottom > room) {
      const scale = room / (top + bottom);
      top = Math.round(top * scale);
      bottom = room - top;
    }
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
