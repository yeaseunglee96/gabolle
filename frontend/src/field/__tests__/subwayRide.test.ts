import { bareStation, nearestExit, parseRideGuidance, subwayRide } from '@/field/subwayRide';
import linesData from '@/field/busanSubwayLines.json';
import stationsRaw from '@/field/busanSubwayStations.json';

describe('지하철 구간 — 방면·정거장 수·출구 (S15P21E201-1881)', () => {
  it('서버 안내 문장에서 탄 역·내린 역을 읽는다 — 환승역의 호선 표시도 뗀다', () => {
    expect(parseRideGuidance('서면역(1호선)에서 1호선을(를) 타고 자갈치역에서 내립니다.')).toEqual({ from: '서면', to: '자갈치' });
    expect(parseRideGuidance('해운대에서 부산역까지 걸어서 갈아탑니다.')).toBeNull();
    expect(bareStation('서면역(2호선)')).toBe('서면');
  });

  it('🔴 방면은 내리는 쪽 종점 — 서면→자갈치는 다대포해수욕장 방면, 반대쪽은 노포 방면', () => {
    expect(subwayRide('1호선', '서면', '자갈치')).toEqual({
      line: '1', from: '서면', to: '자갈치', towards: '다대포해수욕장', opposite: '노포', stopCount: 9,
    });
    // 거꾸로 가면 방면도 거꾸로
    expect(subwayRide('1호선', '자갈치', '서면')?.towards).toBe('노포');
  });

  it('역 순서에서 못 찾으면 짐작하지 않는다', () => {
    expect(subwayRide('1호선', '서면', '해운대')).toBeNull();
    expect(subwayRide('9호선', '서면', '자갈치')).toBeNull();
  });

  it('역 순서의 역은 모두 앱의 역 표에 있다 — 표가 어긋나면 방면을 못 센다', () => {
    const known = new Set((stationsRaw.stations as Array<[string, string, number, number]>).map(([name]) => name));
    for (const order of Object.values(linesData.lines as Record<string, string[]>)) {
      expect(order.filter((name) => !known.has(name))).toEqual([]);
    }
  });

  it('출구 — 목적지에서 가장 가까운 번호, 너무 멀면 말하지 않는다', () => {
    // 자갈치시장(35.0966, 129.0306) — 자갈치역에서 남동쪽
    const exit = nearestExit('자갈치역', 35.0966, 129.0306);
    expect(exit).not.toBeNull();
    expect(exit!.distanceM).toBeLessThan(500);
    expect(nearestExit('자갈치', 35.1587, 129.1604)).toBeNull();
    expect(nearestExit('없는역', 35.0966, 129.0306)).toBeNull();
  });
});
