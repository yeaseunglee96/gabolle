import { distanceText, nearbyStations, STATION_COUNT, stationEnglishName, stationEnglishTitle, straightDistanceM, walkMinutes } from '@/field/subwayStations';
import stationsRaw from '@/field/busanSubwayStations.json';

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

describe('공식 영문 역명 (S15P21E201-1874)', () => {
  it('🔴 읽는 법이 아니라 부산교통공사 영문 역명이다 — 안내판·안내방송과 같아야 역을 찾는다', () => {
    expect(stationEnglishTitle('시청')).toBe('City Hall Station');
    expect(stationEnglishTitle('서면')).toBe('Seomyeon Station');
  });

  it('이미 「Station」이 붙은 이름에 한 번 더 붙이지 않는다', () => {
    expect(stationEnglishName('부산')).toBe('Busan Station');
    expect(stationEnglishTitle('부산')).toBe('Busan Station');
  });

  it('앱이 싣는 역은 모두 공식 영문 역명이 있다 — 빠지면 읽는 법으로 물러선다', () => {
    const names = (stationsRaw.stations as Array<[string, string, number, number]>).map(([name]) => name);
    expect(names.filter((name) => !stationEnglishName(name))).toEqual([]);
  });

  it('원천의 따옴표 겹침 오류를 고친 값이다 — 원천은 작은따옴표가 여러 개 겹쳐 있었다', () => {
    expect(stationEnglishName('부산대')).toBe("Pusan Nat'l Univ.");
  });
});
