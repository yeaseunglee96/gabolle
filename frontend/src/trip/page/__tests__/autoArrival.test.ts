// 자동 도착 — S15P21E201-1568.
//
// 🔴 이 시험이 지키는 것은 「가까이 갔다」와 「머물렀다」를 가르는 것이다. 지나가기만 해도 찍히면 가지도 않은
//    곳이 다녀온 곳으로 남는다. 튀는 위치 하나로 찍혀도 마찬가지다.
import { ARRIVE_DWELL_MS, remainingMeters, stepDwell, type Dwell, type Fix } from '@/trip/page/autoArrival';

const gojae = { id: 'gojae', latitude: 35.1653, longitude: 129.1586 };
const at = (lat: number, lng: number, t: number, accuracy: number | null = 10): Fix => ({ latitude: lat, longitude: lng, accuracy, at: t });
const T0 = 1_000_000;

describe('자동 도착', () => {
  it('50m 안에 2분 머물면 도착이다', () => {
    let dwell: Dwell = null;
    let step = stepDwell(dwell, at(35.1654, 129.1587, T0), gojae);
    expect(step.arrive).toBe(false);
    dwell = step.dwell;
    step = stepDwell(dwell, at(35.1653, 129.1586, T0 + ARRIVE_DWELL_MS), gojae);
    expect(step.arrive).toBe(true);
  });

  it('🔴 지나가기만 하면 도착이 아니다 — 안에 들어왔다가 2분 전에 나가면 다시 센다', () => {
    let step = stepDwell(null, at(35.1654, 129.1587, T0), gojae);
    step = stepDwell(step.dwell, at(35.1700, 129.1586, T0 + 60_000), gojae); // 500m 밖
    expect(step.dwell).toBeNull();
    step = stepDwell(step.dwell, at(35.1653, 129.1586, T0 + ARRIVE_DWELL_MS), gojae);
    expect(step.arrive).toBe(false);
  });

  it('🔴 부정확한 위치(반경 100m 초과)는 안 쓴다 — 튀는 점 하나로 머묾이 시작되지 않는다', () => {
    const step = stepDwell(null, at(35.1653, 129.1586, T0, 350), gojae);
    expect(step.dwell).toBeNull();
    expect(remainingMeters(at(35.1653, 129.1586, T0, 350), gojae)).toBeNull();
  });

  it('다음 곳으로 바뀌면 머묾을 새로 센다 — 앞 장소에서 보낸 시간이 넘어가지 않는다', () => {
    const next = { id: 'buda', latitude: 35.1624, longitude: 129.1630 };
    const step = stepDwell({ stopId: 'gojae', since: T0 }, at(35.1624, 129.1630, T0 + ARRIVE_DWELL_MS), next);
    expect(step.dwell).toEqual({ stopId: 'buda', since: T0 + ARRIVE_DWELL_MS });
    expect(step.arrive).toBe(false);
  });

  it('남은 거리를 미터로 — 고재에서 약 650m 떨어진 영남돼지', () => {
    const left = remainingMeters(at(35.1600, 129.1554, T0), gojae)!;
    expect(left).toBeGreaterThan(600);
    expect(left).toBeLessThan(700);
  });
});
