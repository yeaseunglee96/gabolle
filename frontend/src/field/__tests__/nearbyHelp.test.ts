// 가까운 도움 자료 — S15P21E201-1889 (UI 캔버스 ㉒-4).
import data from '../nearbyHelp.json';
import { HELP_SOURCE_COUNTS, kakaoSearchUrl, nearestHelp, NEARBY_LIMIT_M, telOf } from '../nearbyHelp';

// 광안리해수욕장 앞 — 시안과 같은 자리
const GWANGALLI = { latitude: 35.1532, longitude: 129.1186 };

describe('가까운 도움', () => {
  it('갈래마다 가까운 순 — 반경 안에서 다섯 곳까지', () => {
    for (const kind of ['hospital', 'pharmacy', 'police'] as const) {
      const near = nearestHelp(kind, GWANGALLI);
      expect(near.length).toBeLessThanOrEqual(5);
      expect(near.every((place) => place.kind === kind && place.distanceM <= NEARBY_LIMIT_M)).toBe(true);
      for (let i = 1; i < near.length; i += 1) expect(near[i].distanceM).toBeGreaterThanOrEqual(near[i - 1].distanceM);
    }
  });

  it('광안리에서 경찰은 가까이 있다 — 시안의 광안4치안센터(OSM, 약 617m)', () => {
    const [first] = nearestHelp('police', GWANGALLI);
    expect(first).toBeDefined();
    expect(first.distanceM).toBeLessThan(1000);
  });

  it('🔴 부산 밖에서는 없다고 말한다 — 수십 km 밖의 곳을 「가까운 곳」으로 내지 않는다', () => {
    expect(nearestHelp('hospital', { latitude: 37.5665, longitude: 126.978 })).toEqual([]);
  });

  it('🔴 여행자가 다치거나 아플 때 갈 곳이 아닌 곳은 싣지 않는다 — 요양병원·동물병원·한의원·치과·피부과·성형외과', () => {
    const names = (data.places as Array<[number, number, number, string]>).filter((row) => row[0] === 0).map((row) => row[3]);
    expect(names.some((name) => /요양|동물|한의원|치과|피부|성형/.test(name))).toBe(false);
  });

  it('자료의 갈래 수가 머리의 개수와 같다', () => {
    const rows = data.places as Array<[number]>;
    expect(rows.filter((row) => row[0] === 1).length).toBe(HELP_SOURCE_COUNTS.pharmacy);
  });

  it('전화는 숫자만 · 카카오맵은 갈래 이름으로 찾는다', () => {
    expect(telOf('+82 51-753-2888')).toBe('tel:+82517532888');
    expect(decodeURIComponent(kakaoSearchUrl('pharmacy'))).toBe('https://map.kakao.com/link/search/약국');
  });
});
