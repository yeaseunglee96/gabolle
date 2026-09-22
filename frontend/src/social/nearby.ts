// 「내 근처」 정렬 — 피드의 실시간 인기 탭이 쓴다. S15P21E201-1431.
/** 「내 근처」 — 좌표 있는 기록을 가까운 순으로, 없는 기록은 뒤에 원래 순서대로. 하버사인 없이 위도·경도 차로 충분하다(부산 안). */
export function sortByDistance<T extends { place?: { lat?: number | null; lng?: number | null } | null }>(items: T[], here: { latitude: number; longitude: number }): T[] {
  const dist = (item: T) => {
    const lat = item.place?.lat; const lng = item.place?.lng;
    if (typeof lat !== 'number' || typeof lng !== 'number') return Number.POSITIVE_INFINITY;
    const dLat = lat - here.latitude; const dLng = (lng - here.longitude) * Math.cos((here.latitude * Math.PI) / 180);
    return dLat * dLat + dLng * dLng;
  };
  return items.map((item, index) => ({ item, index, d: dist(item) })).sort((a, b) => (a.d - b.d) || (a.index - b.index)).map((x) => x.item);
}

