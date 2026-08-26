/** 실시간 내비게이션(TripNavigatePage)이 GPS 좌표와 경로를 비교하는 데 쓰는 순수 함수들. */

export function haversineDistanceM(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * path는 [lng, lat] 좌표열(카카오 API 관례)이다. 사용자 위치에서 각 정점까지의
 * 최소 거리를 근사 최근접 거리로 쓴다 - 카카오 경로 좌표열이 촘촘해서(수 미터 간격)
 * 정점 근사만으로도 실용적으로 충분하다(진짜 선분 투영까지는 필요 없음).
 */
export function nearestDistanceToPathM(lat: number, lng: number, path: [number, number][]): number {
  if (!path.length) return Infinity;
  let min = Infinity;
  for (const [pLng, pLat] of path) {
    const d = haversineDistanceM(lat, lng, pLat, pLng);
    if (d < min) min = d;
  }
  return min;
}

/** 여러 구간(steps) 중 사용자 위치와 가장 가까운 구간의 인덱스를 찾는다. */
export function nearestStepIndex(lat: number, lng: number, steps: { path: [number, number][] }[]): number {
  let bestIndex = 0;
  let bestDistance = Infinity;
  steps.forEach((step, index) => {
    const d = nearestDistanceToPathM(lat, lng, step.path);
    if (d < bestDistance) { bestDistance = d; bestIndex = index; }
  });
  return bestIndex;
}

/** 도보 구간은 GPS+경로 스냅 오차가 작고, 대중교통 구간은 차량 자체 오차가 커서 허용 반경을 다르게 둔다. */
export function offRouteThresholdM(vehicle: string | null): number {
  if (!vehicle || vehicle.includes("도보") || /walk/i.test(vehicle)) return 80;
  return 200;
}
