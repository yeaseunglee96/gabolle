import { distanceText, nearbyStations, STATION_COUNT, straightDistanceM, walkMinutes } from '@/field/subwayStations';

describe('가까운 지하철역 (S15P21E201-1830)', () => {
  it('환승역은 한 번만 — 서면은 1·2호선 한 줄', () => {
    const seomyeon = nearbyStations({ latitude: 35.1578, longitude: 129.0594 }, 300, 5);
    expect(seomyeon[0].name).toBe('서면');
    expect(seomyeon[0].lines).toEqual([1, 2]);
    expect(seomyeon.filter((s) => s.name === '서면')).toHaveLength(1);
    expect(STATION_COUNT).toBeGreaterThan(100);
  });

  it('반경 밖의 역은 안 낸다 — 바다 한가운데면 빈 목록', () => {
    expect(nearbyStations({ latitude: 35.0, longitude: 129.4 })).toEqual([]);
  });

  it('가까운 순으로 셋까지', () => {
    const got = nearbyStations({ latitude: 35.1587, longitude: 129.1604 });
    expect(got.length).toBeLessThanOrEqual(3);
    expect(got[0].name).toBe('해운대');
    for (let i = 1; i < got.length; i += 1) expect(got[i].distanceM).toBeGreaterThanOrEqual(got[i - 1].distanceM);
  });

  it('거리·걷는 시간 글자', () => {
    expect(Math.round(straightDistanceM({ latitude: 35, longitude: 129 }, { latitude: 35.001, longitude: 129 }))).toBe(111);
    expect(distanceText(3)).toBe('10m');
    expect(distanceText(574)).toBe('570m');
    expect(distanceText(1234)).toBe('1.2km');
    expect(walkMinutes(10)).toBe(1);
    expect(walkMinutes(700)).toBe(13);
  });
});
