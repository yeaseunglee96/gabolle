import { ALERT_M, rideLegs, rideProgress } from '@/field/ride';
import type { RouteDirections } from '@/map/routeDirections';

// 서면 → 전포 → 부전(부산 1호선 실제 좌표 근처) — 백엔드 TransitRouteAdapterStopsTest 와 같은 자리.
const SEOMYEON = { name: '서면', lat: 35.1580, lng: 129.0594 };
const JEONPO = { name: '전포', lat: 35.1520, lng: 129.0648 };
const BUJEON = { name: '부전', lat: 35.1449, lng: 129.0621 };

const directions = (steps: unknown[], path: [number, number][] = []): RouteDirections => ({
  mode: 'TRANSIT', distanceM: 1500, durationMin: 10, taxiFareKrw: null, tollFareKrw: null, transferCount: 0, estimated: true, estimateReason: null, provider: 'TRANSIT_NETWORK', path, steps: steps as RouteDirections['steps'],
});
const ride = { name: '1호선', guidance: '서면에서 1호선을(를) 타고 부전에서 내립니다.', distanceM: 1500, durationMin: 4 };

describe('탑승 중 (S15P21E201-1837)', () => {
  it('타는 구간 — 노선·타는 곳·내리는 곳·정류장 목록', () => {
    const [leg] = rideLegs(directions([{ name: '도보', guidance: '', distanceM: 80, durationMin: 1 }, { ...ride, stops: [SEOMYEON, JEONPO, BUJEON] }]));
    expect(leg).toMatchObject({ kind: 'subway', line: '1호선', board: '서면', alightName: '부전', alight: { latitude: BUJEON.lat, longitude: BUJEON.lng } });
    expect(leg.stops.map((stop) => stop.name)).toEqual(['서면', '전포', '부전']);
  });

  it('버스는 번호 끝의 「번」을 뗀다', () => {
    const [leg] = rideLegs(directions([{ name: '1003번', guidance: '해운대해수욕장입구에서 1003번을(를) 타고 자갈치역.비프광장에서 내립니다.', distanceM: 1, durationMin: 1 }]));
    expect(leg).toMatchObject({ kind: 'bus', line: '1003', board: '해운대해수욕장입구', alightName: '자갈치역.비프광장' });
  });

  it('옛 서버(정류장 목록 없음) — 한 번만 타면 경로선 끝에서 두 번째 점이 내릴 곳', () => {
    const path: [number, number][] = [[129.06, 35.159], [SEOMYEON.lng, SEOMYEON.lat], [JEONPO.lng, JEONPO.lat], [BUJEON.lng, BUJEON.lat], [129.063, 35.144]];
    const [leg] = rideLegs(directions([ride], path));
    expect(leg.stops).toEqual([]);
    expect(leg.alight).toEqual({ latitude: BUJEON.lat, longitude: BUJEON.lng });
    // 거리로만 센다 — 남은 정류장은 모른다
    const progress = rideProgress(leg, { latitude: SEOMYEON.lat, longitude: SEOMYEON.lng });
    expect(progress.remainingStops).toBeNull();
    expect(progress.distanceToAlightM).toBeGreaterThan(ALERT_M);
    expect(progress.alertNow).toBe(false);
  });

  it('갈아타면 경로선으로 내릴 곳을 짐작하지 않는다 — 어느 점인지 모른다', () => {
    const legs = rideLegs(directions([ride, { name: '2호선', guidance: '서면에서 2호선을(를) 타고 전포에서 내립니다.', distanceM: 1, durationMin: 1 }], [[0, 0], [1, 1], [2, 2]]));
    expect(legs.map((leg) => leg.alight)).toEqual([null, null]);
  });

  it('남은 정류장·다음 정류장·알릴 때·내렸을 때', () => {
    const [leg] = rideLegs(directions([{ ...ride, stops: [SEOMYEON, JEONPO, BUJEON] }]));
    const atStart = rideProgress(leg, { latitude: SEOMYEON.lat, longitude: SEOMYEON.lng });
    expect(atStart).toMatchObject({ remainingStops: 2, nextStop: '전포', alertNow: false, arrived: false });
    const oneBefore = rideProgress(leg, { latitude: JEONPO.lat, longitude: JEONPO.lng });
    expect(oneBefore).toMatchObject({ remainingStops: 1, nextStop: '부전', alertNow: true, arrived: false });
    const there = rideProgress(leg, { latitude: BUJEON.lat, longitude: BUJEON.lng });
    expect(there).toMatchObject({ remainingStops: 0, nextStop: null, alertNow: false, arrived: true });
  });

  it('대중교통이 아니면 구간이 없다', () => {
    expect(rideLegs({ ...directions([ride]), mode: 'WALK' })).toEqual([]);
  });
});
