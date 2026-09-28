// 경로 선 단순화 — 줌이 멀 때 화면에서 안 보이는 작은 꺾임을 덜어 낸다(S15P21E201-1656).
//
// 경로 모양은 카카오 자동차 길이라 꺾임점이 촘촘하고 나들목 고리·램프를 그대로 따라간다. 멀리서 보면 그 꺾임이
// 몇 픽셀 안에 몰려 선이 떨려 보인다. 더글러스-푀커(Douglas–Peucker) 방식으로 덜어 낸다 — 처음과 끝을 잇는
// 직선에서 가장 멀리 떨어진 점이 허용치보다 가까우면 사이 점을 모두 버리고, 멀면 그 점에서 둘로 나눠 되풀이한다.
//
// 🔴 앱 지도(kakaoMapHtml.ts 의 simplify)가 같은 셈을 글자로 한 벌 더 들고 있다 — WebView 안의 스크립트는 이 파일을
//    못 읽는다. 여기를 고치면 거기도 고친다.

export type LatLngPoint = { latitude: number; longitude: number };

/** 점 p 에서 선분 ab 까지의 거리(평면 좌표, 미터). */
function distanceToSegment(p: [number, number], a: [number, number], b: [number, number]): number {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const lengthSq = dx * dx + dy * dy;
  const t = lengthSq === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lengthSq));
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
}

/** 허용치(미터)보다 작은 꺾임을 덜어 낸 점들. 처음과 끝은 늘 남는다. 점이 셋보다 적거나 허용치가 0 이면 그대로 돌려준다. */
export function simplifyPath<T extends LatLngPoint>(points: T[], toleranceMeters: number): T[] {
  if (points.length < 3 || !(toleranceMeters > 0)) return points;
  // 한 도시 안의 선이라 첫 점의 위도에서 평면으로 편다 — 그 오차는 픽셀 몇 개 허용치에 비하면 무시할 만하다.
  const metersPerLng = 111320 * Math.cos((points[0].latitude * Math.PI) / 180);
  const metersPerLat = 110540;
  const xy = points.map((point) => [point.longitude * metersPerLng, point.latitude * metersPerLat] as [number, number]);
  const keep = new Array<boolean>(points.length).fill(false);
  keep[0] = true;
  keep[points.length - 1] = true;
  const spans: Array<[number, number]> = [[0, points.length - 1]];
  while (spans.length) {
    const [from, to] = spans.pop() as [number, number];
    let farthest = -1;
    let farthestDistance = toleranceMeters;
    for (let i = from + 1; i < to; i += 1) {
      const distance = distanceToSegment(xy[i], xy[from], xy[to]);
      if (distance > farthestDistance) { farthestDistance = distance; farthest = i; }
    }
    if (farthest >= 0) {
      keep[farthest] = true;
      spans.push([from, farthest], [farthest, to]);
    }
  }
  return points.filter((_, index) => keep[index]);
}
